# ChatModelAgentProvider Concurrency Limiter

**Issue:** casehubio/platform#125
**Date:** 2026-06-29
**Status:** Design

## Context

`ClaudeAgentProvider` uses a `Semaphore(maxConcurrentSessions)` for backpressure — `tryAcquire()` at entry, three-path release (completion/failure/cancellation), `AgentSessionLimitException` on capacity. `ChatModelAgentProvider` has no throttling. With the streaming upgrade (#120), each call holds an HTTP connection for the entire token generation duration, making unbounded concurrency a resource and cost risk.

## Decision

Mirror the Claude semaphore pattern in `ChatModelAgentProvider`. Fail-fast via `tryAcquire()` — no blocking, no queuing.

**Fail-fast vs timeout acquisition:** Issue #125 mentions "timeout on permit acquisition to avoid indefinite blocking." We chose fail-fast (`tryAcquire()`) to match the Claude pattern and avoid blocking reactive threads. On Vert.x, `tryAcquire(timeout, unit)` blocks the calling thread — unacceptable on the event loop. Fail-fast returns immediately; callers retry at their discretion.

## Design

### Config

New property on `AgentLangchain4jProperties`:

```java
@WithDefault("10")
int maxConcurrentSessions();
```

Config key: `casehub.platform.agent.langchain4j.max-concurrent-sessions`

Default 10 (vs Claude's 4) — LangChain4j wraps generic ChatModels where backend capacity varies. Operators tune for their provider.

Property named `maxConcurrentSessions` to match `ClaudeAgentProperties.maxConcurrentSessions()`. Both properties govern the same semaphore concept — concurrent permits for `invoke()` and `openSession()`. Using the same name across both `AgentProvider` implementations makes the config coherent.

### ChatModelAgentProvider

New field: `Semaphore semaphore` initialized in `@PostConstruct init()` from `properties.maxConcurrentSessions()`. Non-final, matching the existing `chatModel` / `streamingChatModel` / `disabled` fields set in the same method. Field injection makes `properties` unavailable at construction time.

`init()` validates `maxConcurrentSessions >= 1` before constructing the Semaphore — throws `IllegalStateException` for zero or negative values. `Semaphore(0)` would reject all calls with misleading diagnostics (WARN log says "0 active sessions", indistinguishable from genuine overload). A negative value creates undefined behavior.

Package-private `availablePermits()` method for test assertions — matches `ClaudeAgentClient.availablePermits()`.

**`invoke()`:**

The existing `disabled` guard precedes all semaphore operations. If the provider is inactive, return failure immediately — no semaphore interaction.

1. `tryAcquire()` — false → WARN log + `Multi.createFrom().failure(new AgentSessionLimitException(...))`. No termination handlers on this failure Multi — no permit was acquired.
2. `try` block: build the ChatModel call Multi, wire three-path release handlers (`onCompletion`, `onFailure`, `onCancellation` → `semaphore.release()`). Return this Multi.
3. `catch` block (sync exception during Multi construction): `semaphore.release()`, return `Multi.createFrom().failure(e)`. This bare failure Multi has **NO** three-path handlers — the catch already released the permit.

The try and catch paths are **mutually exclusive** — exactly one release mechanism applies. An implementor must not wire three-path handlers on the catch path's failure Multi, or the permit will be double-released.

Streaming calls hold the permit until the stream completes — not until the first token.

**`openSession()`:**

The existing `disabled` guard precedes all semaphore operations. If the provider is inactive, throw immediately — no semaphore interaction.

1. `tryAcquire()` — false → WARN log + throw `AgentSessionLimitException`
2. `new ChatModelAgentSession(...)` wrapped in try/catch — sync exception releases permit
3. Semaphore passed to session constructor

### ChatModelAgentSession

New constructor parameter: `Semaphore semaphore`.

**Query termination (all three paths — completion, failure, cancellation):** CAS ACTIVE → IDLE. **No semaphore release.** Session accepts subsequent queries. This preserves existing behavior — ChatModel wraps stateless HTTP APIs where failures are transient (rate limits, network blips, model timeouts). None invalidate the session's `ChatMemory` or the underlying `ChatModel` bean. Contrast with `ClaudeAgentSession`, where failure/cancellation → CLOSED because a subprocess failure means the process is dead.

**`close()`:** `getAndSet(State.CLOSED)` — if previous state was not CLOSED, release semaphore. Idempotent: second call sees CLOSED and skips the release. Matches the `ClaudeAgentSession.close()` double-release prevention pattern.

One permit per session lifetime. No per-query acquire/release.

**Known limitation — no drain on close():** `close()` does not wait for an active streaming query to complete before releasing the semaphore. If `close()` is called during an active stream, the permit is released while the underlying HTTP stream may still be running — actual HTTP connections can briefly exceed the concurrency limit. The overcount is bounded (the stream completes on its own timeline) and the semaphore's purpose is cost-control backpressure, not HTTP connection pool management. Drain logic (matching `ClaudeAgentSession`'s `maxWait`-based drain with `CompletableFuture` per turn) is a separate concern from the concurrency limiter.

### Observability

WARN log on `tryAcquire()` failure in both `invoke()` and `openSession()`: includes available/total counts for capacity tuning. Without this, operators whose limit is too low have no signal — requests fail with `AgentSessionLimitException` but the reason isn't visible in logs.

### What does NOT change

- `AgentSessionConfig` / `AgentSessionInit` — no new fields
- `AgentEventBridge` — untouched
- `AgentProviderChatModel` / `AgentSessionChatModel` — these wrap AgentProvider for LangChain4j consumers; they don't own concurrency

## Testing

Unit tests with a real `Semaphore(1)` for deterministic capacity assertions:

**invoke():**
- At capacity → `AgentSessionLimitException`
- Releases permit on completion — subsequent call succeeds
- Releases permit on failure — no leak
- Releases permit on cancellation — no leak
- Sync exception during Multi construction releases permit (bare failure Multi, no three-path handlers)
- Two concurrent streaming calls at `maxConcurrentSessions=1` — second fails immediately while first stream is active

**openSession():**
- At capacity → `AgentSessionLimitException`
- Construction failure releases permit
- Session `close()` releases permit — subsequent `openSession()` succeeds
- Session `close()` idempotent — double close doesn't double-release semaphore
- Session query completion does NOT release permit (session stays IDLE)
- Session query failure does NOT release permit (session stays IDLE)
- Session query cancellation does NOT release permit (session stays IDLE)

**Streaming path:**
- Holds permit until stream completes (not until first token)

## Deferred

- **`ClaudeAgentProperties.maxConcurrentSessions()` validation** (#126) — same gap as R2-03: no `>= 1` guard.
- **Session drain on close()** — `ChatModelAgentSession.close(Duration)` ignores `maxWait` (pre-existing from 2026-06-26 spec). The concurrency limiter makes the consequence slightly more visible but does not change the underlying gap. Drain logic is a separate concern.

## Scope

- Files changed: `AgentLangchain4jProperties.java`, `ChatModelAgentProvider.java`, `ChatModelAgentSession.java`
- Files added: test classes for new semaphore behaviour
- No Flyway migrations. No new modules. No SPI changes.
