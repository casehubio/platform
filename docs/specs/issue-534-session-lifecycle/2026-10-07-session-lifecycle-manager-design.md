# Session Lifecycle Manager Design

**Issue:** casehubio/platform#534
**Date:** 2026-10-07
**Status:** Draft

## Problem

Every LLM call in the platform spawns a new CLI process via `AgentProvider.invoke()`. For Claude CLI, this adds 5-10s cold-start overhead per call. With 16+ calls per cognitive tick (appraisal, knowledge graph extraction, reflection, belief revision, narrative, strategy, user model, mental model, inner life, goal enrichment, situation classification), the overhead dominates runtime.

The platform has the primitives — `AgentProvider.openSession()` returns a persistent `AgentSession`, and `AgentSessionChatModel` wraps it as a `ChatModel` — but no component manages session lifecycle: creation, pooling, reuse, and eviction.

## Evidence

- 17 active LLM call sites in neocortex, all using one-shot `invoke()`, all stateless single-shot
- Zero call sites use `openSession()` in production code
- Zero call sites specify a model — all use the default
- Appraisal prototype with manual `openSession()`: 0 failures, sub-second after first call
- Wacky-manor tick with all one-shot `invoke()`: ~3 minutes (16 CLI cold starts)

## Design

### Architecture

Three components in a new `agent-session-core` module:

```
┌─────────────────────────────────────────────────────┐
│  Callers (neocortex LLM call sites)                 │
│  ┌──────────────────┐  ┌──────────────────────────┐ │
│  │ invoke() callers  │  │ Explicit SPI callers     │ │
│  │ (zero migration)  │  │ (conversation identity)  │ │
│  └────────┬─────────┘  └──────────┬───────────────┘ │
└───────────┼─────────────────────────┼───────────────┘
            │                         │
  ┌─────────▼─────────┐   ┌──────────▼──────────────┐
  │ RoutingAgent       │   │SessionLifecycleManager  │
  │ Provider (pool-    │   │(explicit SPI)           │
  │ aware routing)     │   └──────────┬──────────────┘
  └─────────┬─────────┘              │
            └──────────┬──────────────┘
                       │
              ┌────────▼────────┐
              │   SessionPool   │
              │ (per backend +  │
              │  model tier)    │
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │  AgentBackend   │
              │ (Claude, OpenAI,│
              │  LangChain4j)   │
              └─────────────────┘
```

### Call Chain Integration

The pool is a collaborator of `RoutingAgentProvider`, not a separate decorator. The router already resolves the backend — once resolved, it delegates through the pool instead of directly to `backend.invoke()`:

```
GatedAgentProviderWrapper @Priority(2000) (rate limiting callers)
  → RoutingAgentProvider (resolves model → backend → pool)
    → SessionPool.checkout() → AgentSession.query()
    → SessionPool.release()
```

`SessionPoolRegistry` is injected into `RoutingAgentProvider` as an optional collaborator. When present, the router routes through the pool after resolution. When absent (no pool configured), the router calls `backend.invoke()` directly — backward-compatible.

When `invoke()` is called:
1. Gate admits the caller (rate limiting)
2. Router resolves the model to a backend
3. Router looks up pool for `(backend.key(), resolvedModel)` via `SessionPoolRegistry`
4. If pool exists: checkout warm session, `session.query(userPrompt)`, release on completion
5. If no pool: fall through to `backend.invoke()` (current behavior)

### Component Details

#### SessionPool

One pool per (backend key, model tier) pair. Manages a bounded set of `AgentSession` instances.

```java
public class SessionPool {

    SessionPool(AgentBackend backend, SessionPoolConfig config,
                ScheduledExecutorService scheduler);

    PooledSession checkout(AgentSessionInit init);

    void release(PooledSession session);

    void shutdown();
}
```

**Pool states for a session slot:**
- **Warm** — subprocess running, no active query, ready for checkout
- **Checked-out** — in use by a caller
- **Starting** — subprocess being created (async pre-warm)

**Pre-warming:** A background thread maintains `minActive` warm sessions. When a session is released (closed + destroyed), the pool immediately starts warming a replacement. Pre-warming uses `backend.openSession(init)` followed by a no-prompt `connect()` where supported.

**Eviction:** Sessions idle beyond `idleTimeoutSeconds` are closed. The pool never drops below `minActive` warm sessions unless shutting down.

**Sizing:** `minActive` and `maxActive` from `PoolDeclaration`. If no pool declaration exists for a backend, the pool operates in pass-through mode (creates on demand, no pre-warming).

#### PooledSession

Wraps an `AgentSession` with pool metadata. Returned from `SessionPool.checkout()`.

```java
public class PooledSession implements AgentSession {

    AgentSession delegate();
    String backendKey();
    Instant checkedOutAt();

    // AgentSession delegation
    Multi<AgentEvent> query(String prompt);  // delegates
    Uni<Void> interrupt();                    // delegates
    void close(Duration maxWait);             // releases back to pool
    void close();                             // releases back to pool
}
```

`close()` on a `PooledSession` does NOT destroy the session — it releases it back to the pool. The pool decides whether to keep it warm or destroy it.

For CLI backends (where clearing = destroying), `close()` destroys the inner session and the pool pre-warms a replacement. For ChatModel backends, `close()` calls `clear()` on the inner session (resetting ChatMemory) and returns it to the warm set.

#### SessionLifecycleManager

The explicit SPI for callers that want conversation identity. Manages a map of conversation IDs to checked-out sessions.

```java
public class SessionLifecycleManager {

    SessionLifecycleManager(Map<String, SessionPool> pools);

    ManagedSession acquire(String conversationId, AgentSessionInit init);

    void release(String conversationId);

    void close(String conversationId);
}
```

**Sticky binding:** Once a conversation ID is bound to a session, subsequent `acquire()` calls return the same session. The session is NOT returned to the pool between calls — it stays checked out.

**`ManagedSession`** extends `PooledSession` with conversation identity:

```java
public class ManagedSession extends PooledSession {
    String conversationId();
}
```

**Clearing policies** (configurable per conversation ID):
- `EVERY_CALL` — clear context after every query (stateless, same as the decorator)
- `MANUAL` — caller controls when to clear (default for explicit SPI)
- `AFTER_N_TURNS` — clear after N turns (configurable)
- `ON_THRESHOLD` — clear when token usage approaches a threshold (future)

#### Pool Integration in RoutingAgentProvider

The pool is not a separate decorator — it's wired into `RoutingAgentProvider` as an optional `SessionPoolRegistry` collaborator. This avoids the problem of a decorator not knowing which backend was resolved.

Changes to `RoutingAgentProvider`:

```java
public class RoutingAgentProvider implements AgentProvider {

    private final SessionPoolRegistry poolRegistry; // nullable — optional

    Multi<AgentEvent> invoke(AgentSessionConfig config) {
        ResolvedRoute route = resolve(config);
        AgentSessionConfig rewritten = rewrite(config, route);

        SessionPool pool = poolRegistry != null
            ? poolRegistry.findPool(route.backend().key(), rewritten.model()).orElse(null)
            : null;

        if (pool != null) {
            return pool.invokeViaSession(rewritten);
        }
        return route.backend().invoke(rewritten);
    }

    AgentSession openSession(AgentSessionInit init) {
        ResolvedRoute route = resolve(init);
        AgentSessionInit rewritten = rewrite(init, route);

        SessionPool pool = poolRegistry != null
            ? poolRegistry.findPool(route.backend().key(), rewritten.model()).orElse(null)
            : null;

        if (pool != null) {
            return pool.checkout(rewritten);
        }
        return route.backend().openSession(rewritten);
    }
}
```

**`pool.invokeViaSession(config)`:** Checkout → `session.query(config.userPrompt())` → release on completion/failure/cancellation. Returns the event stream directly.

**`pool.checkout(init)`:** Returns a `PooledSession` whose `close()` releases back to the pool instead of destroying.

When `SessionPoolRegistry` is absent (not on classpath), the router behaves identically to today — direct delegation to the backend.

#### SessionPoolRegistry

Maps (backend key, model) pairs to `SessionPool` instances. Created at startup from `PoolDeclaration` configs.

```java
public class SessionPoolRegistry {

    SessionPoolRegistry(List<PoolDeclaration> declarations,
                        BackendInstanceRegistry backends);

    SessionPool poolFor(String backendKey, String model);

    Optional<SessionPool> findPool(String backendKey, String model);
}
```

If no pool declaration exists for a backend, `poolFor()` returns a pass-through pool that creates sessions on demand with no pre-warming (backward-compatible with current behavior).

### Changes to Existing Modules

#### agent-api

Add `clear()` with default no-op to `AgentSession`:

```java
default void clear() {
    // No-op by default. Backends that support in-place context reset override this.
}
```

#### agent-claude-core

`ClaudeAgentSession.clear()` — initially delegates to no-op. The Claude Agent SDK (1.0.0) does not expose a clear/reset command. When the SDK adds support, this method overrides with an in-place reset.

Document the limitation:
```java
@Override
public void clear() {
    // Claude CLI SDK 1.0.0 has no clear() — subprocess restart is the only reset path.
    // The pool handles this by closing the session and pre-warming a replacement.
    // When the SDK adds /clear support, override here for in-place reset.
}
```

#### agent-langchain4j-core

`ChatModelAgentSession.clear()` — reset the `ChatMemory`:

```java
@Override
public void clear() {
    memory.clear();
}
```

This is an in-place reset — the session stays alive and ready for the next query.

#### agent-config-core

No changes. `PoolDeclaration` already has `minActive`, `maxActive`, `backend`, and `agentId`. The `extensions` map can carry additional pool config (idle timeout, model tier).

### Module Structure

```
agent-session-core/
  pom.xml                     — depends on agent-api, agent-config-core
  src/main/java/io/casehub/platform/agent/session/
    SessionPool.java
    SessionPoolConfig.java
    SessionPoolRegistry.java
    PooledSession.java
    ManagedSession.java
    SessionLifecycleManager.java
    ClearingPolicy.java        — enum: EVERY_CALL, MANUAL, AFTER_N_TURNS
    PassThroughPool.java       — no-pool fallback (create on demand)

agent-session/
  pom.xml                     — depends on agent-session-core (CDI wiring)
  src/main/java/io/casehub/platform/agent/session/quarkus/
    SessionBeans.java          — @Produces SessionPoolRegistry, SessionLifecycleManager
```

### Configuration

Pool declarations in `agent-config.yaml`:

```yaml
pools:
  - name: claude-default
    agent-id: claude
    backend: claude
    min-active: 2
    max-active: 5
    extensions:
      idle-timeout-seconds: 300
      model-tier: FLAGSHIP

  - name: haiku-pool
    agent-id: claude-haiku
    backend: claude
    min-active: 1
    max-active: 3
    extensions:
      idle-timeout-seconds: 120
      model-tier: FAST
```

When no pool is declared for a backend, the system operates in pass-through mode — identical to current behavior (create on demand, no pooling).

### Interaction with agent-gate

The gate's `ConcurrencyStrategy` continues to limit concurrent callers at the `AgentProvider` level. The pool limits concurrent processes at the backend level. These are independent concerns:

- **Gate:** "How many callers can invoke concurrently?" (rate limiting)
- **Pool:** "How many processes exist?" (resource management)

The gate's `SessionRegistry` and `SessionLeakReaper` continue to track `GatedAgentSession` instances. The pool's sessions are tracked internally by the pool — the reaper doesn't need to know about them.

Backend-level semaphores in `ClaudeAgentClient` and `ChatModelAgentProvider` become redundant when a pool is configured. The pool's `maxActive` replaces the backend semaphore's role. When no pool is configured (pass-through mode), the backend semaphore continues to function.

### Testing Strategy

- **Unit tests:** `SessionPool` with mock `AgentBackend` — verify checkout/release/pre-warm/eviction lifecycle
- **Unit tests:** `PooledAgentProvider` — verify invoke() routes through pool correctly
- **Unit tests:** `SessionLifecycleManager` — verify sticky binding, clearing policies
- **Contract test:** `SessionPoolContractTest` abstract base — backends implement to verify pool integration
- **Integration test:** `ClaudeAgentSession` with pool — verify close-and-replace cycle

### Migration Path (neocortex#472)

Once the lifecycle manager lands in platform, neocortex callers adopt it:

**Phase 1 — Zero-migration pooling:** Enable `PooledAgentProvider` decorator. All 17 `invoke()` callers automatically get process reuse. No code changes in neocortex.

**Phase 2 — Explicit session adoption:** Migrate individual subsystems to `SessionLifecycleManager` for conversation identity:
- `LlmAppraisalStrategy` → conversation ID `"appraisal"`, clearing policy `EVERY_CALL`
- `MindMapExtractor` → conversation ID `"kg-extractor"`, clearing policy `EVERY_CALL`
- `LlmReflectionSynthesizer` → conversation ID `"reflection"`, clearing policy `EVERY_CALL`
- Other subsystems → conversation IDs per subsystem, policies per use case

Phase 2 is incremental and optional. Phase 1 delivers the primary value.

### Risks

1. **Claude SDK clear() gap:** The Claude Agent SDK has no clear/reset. Clearing = subprocess restart. Pool compensates with pre-warming, but the amortized cost is non-zero. Mitigated by sizing `minActive` to cover replacement latency.

2. **Pool exhaustion:** If all sessions are checked out and a new caller arrives, the pool blocks (up to a configurable timeout) or creates a new session up to `maxActive`. Beyond `maxActive`, callers get `AgentSessionLimitException`. Mitigated by sizing pools appropriately and monitoring via `PoolMetrics`.

3. **Stale sessions:** A warm session's subprocess may crash silently. The pool needs health checking — either periodic ping or failure-on-first-query with automatic retry from a fresh session.

4. **Router coupling:** The pool is injected into `RoutingAgentProvider` as a collaborator, creating a dependency from `agent-router-core` to `agent-session-core`. This is acceptable — one-directional and optional (nullable injection). The router works identically without it.

## References

- `AgentProvider.java` (agent-api) — SPI with invoke() and openSession()
- `AgentSession.java` (agent-api) — session interface, no clear()
- `ClaudeAgentSession.java` (agent-claude-core) — Claude CLI session implementation
- `ChatModelAgentSession.java` (agent-langchain4j-core) — LangChain4j session with ChatMemory
- `ClaudeAsyncClient.java` (claude-code-sdk 1.0.0) — SDK interface, no clear()
- `SessionRegistry.java` (agent-gate-core) — existing session tracking
- `PoolDeclaration.java` (agent-config-core) — existing pool config type
- `RoutingAgentProvider.java` (agent-router-core) — model resolution and backend routing
- `GatedAgentProviderWrapper.java` (agent-gate-core) — admission control decorator
- Issue casehubio/platform#534 — session lifecycle management
- Issue casehubio/neocortex#472 — neocortex adoption
