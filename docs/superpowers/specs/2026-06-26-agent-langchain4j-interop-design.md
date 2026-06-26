# Design: Agent–LangChain4j Interop + MissingTenancyExceptionMapper

**Issues:** #114, #115, #105  
**Date:** 2026-06-26  
**Branch:** issue-114-agent-docs-mapper-bridge

---

## Context

The platform has two agent/model abstraction layers that evolved independently:

- **AgentProvider** (platform `agent-api/`) — reactive streaming (`Multi<AgentEvent>`), managed
  session lifecycle (semaphores, drain, close), correlation-based async, cancellation, typed
  error taxonomy. Two implementations: `ClaudeAgentProvider` (Claude CLI subprocess) and
  `OpenClawAgentProvider` (webhook-based async).

- **ChatModel / StreamingChatModel** (LangChain4j) — the de facto standard Java LLM interface.
  JSON response format, tool use protocol, provider-specific parameters, multi-modal input.
  Five engine implementations (Anthropic, OpenAI, Gemini, Ollama, Mistral).

These layers don't interoperate by design. Bridges exist but are provider-specific: both
`ClaudeAgentChatModel` and `OpenClawChatModel` follow the same structural pattern (extract
messages → call AgentProvider → collect TextDelta → build ChatResponse) but differ in
implementation details — session vs invoke, streaming vs blocking, JSON handling, listeners,
error handling. The reverse direction (ChatModel → AgentProvider) doesn't exist at all.

### Why both exist

AgentProvider exists because some providers have native SDK advantages that LangChain4j can't
express. Claude CLI's prompt caching is the primary example — the CLI manages `cache_control`
breakpoints transparently at the Anthropic API level. LangChain4j's `SystemMessage` has no
`cache_control` field, and LangChain4j mediates every call, so even a native SDK behind
`doChat()` loses caching control.

LangChain4j's streaming handler is richer (`onPartialThinking`, `onPartialToolCall`,
`onCompleteToolCall`) than AgentEvent's `TextDelta`-only sealed interface. Platform infrastructure
wrapping (Mutiny, typed errors, concurrency limits) could be layered on ChatModel — AgentProvider
packages them coherently but doesn't provide capabilities LangChain4j lacks.

### Design principle

AgentProvider is the platform's agent SPI. Two implementation strategies:

1. **Native SDK** — bypasses LangChain4j for providers where the native SDK offers advantages.
   `ClaudeAgentProvider` is the current example. Future native implementations are possible.

2. **LangChain4j-backed** — the default for everything else. Any LangChain4j
   `ChatModel`/`StreamingChatModel` works as an AgentProvider with zero provider-specific code.

Engine code that needs `ChatModel` (for JSON mode, tool use, parameters) gets it via a generic
adapter that wraps any AgentProvider. This replaces per-provider adapters.

---

## Issue #115 — MissingTenancyExceptionMapper

### Problem

`MissingTenancyException` is thrown by `OidcCurrentPrincipal.tenancyId()` when the JWT lacks the
`tenancyId` claim. Without an `ExceptionMapper`, RESTEasy maps it to HTTP 500. Every OIDC consumer
duplicates their own mapper.

### Design

**Module:** `oidc/`

**Class:** `MissingTenancyExceptionMapper implements ExceptionMapper<MissingTenancyException>`

**Status code:** 403 Forbidden — consistent with Quarkus's own pattern for valid-token-but-
insufficient-claims (`@RolesAllowed` failure → 403). The client authenticated but lacks a required
authorization attribute (tenancyId claim).

**Response body:**
```json
{
  "error": "missing_tenancy",
  "message": "JWT does not contain a tenancyId claim",
  "actorId": "alice"
}
```

The `actorId` is read from `MissingTenancyException.actorId()` — no `CurrentPrincipal` injection
is needed. Including the actorId is safe — the caller already knows their own identity. It aids
operator diagnostics (which principal has the misconfigured token).

**Content-Type:** `application/json`

### Files

| File | Purpose |
|------|---------|
| `oidc/.../MissingTenancyExceptionMapper.java` | ExceptionMapper producing 403 + JSON body |
| `oidc/.../MissingTenancyExceptionMapperTest.java` | Unit test: status code, body fields, content type |

---

## Issue #114 — AgentProvider design rationale documentation

### Location

`agent-api/README.md` — the natural home. Consumers reading the module find the rationale
immediately.

### Content

1. **Why AgentProvider exists** — native SDK advantages that LangChain4j can't express (Claude
   CLI prompt caching, MCP server wiring, subprocess lifecycle management).

2. **Two implementation strategies** — native SDK (bypass LangChain4j) vs LangChain4j-backed
   (default for all other providers).

3. **CDI tier structure** — two separate tier systems:
   - AgentProvider: `@DefaultBean` (NoOp) → `@Alternative @Priority(1)` (LangChain4j) →
     `@Alternative @Priority(10)` (Claude native)
   - ChatModel: `@DefaultBean` (quarkus-langchain4j) → `@DefaultBean @Priority(10)`
     (`AgentProviderChatModel`); consumer non-`@DefaultBean` overrides both

4. **Interop with engine** — `AgentProviderChatModel` adapter wraps any AgentProvider as a
   ChatModel. Supports JSON format via prompt engineering.

5. **Not for use with engine.Agent (directly)** — engine's `Agent` forces
   `ResponseFormatType.JSON`. Use the `AgentProviderChatModel` adapter, which prompt-engineers
   JSON schemas into the user text.

---

## Issue #105 — agent-langchain4j/ module

### Module purpose

`casehub-platform-agent-langchain4j` is the LangChain4j interop module. Bidirectional:

- **ChatModel → AgentProvider:** wraps any LangChain4j model as a platform AgentProvider
- **AgentProvider → ChatModel:** wraps any AgentProvider as a LangChain4j ChatModel

### Artifact

- GroupId: `io.casehub`
- ArtifactId: `casehub-platform-agent-langchain4j`
- Package: `io.casehub.platform.agent.langchain4j`

### Dependencies

- `casehub-platform-agent-api` (AgentProvider SPI)
- `langchain4j-core` (ChatModel, StreamingChatModel, ChatMemory)
- `quarkus-arc` (CDI)
- Test: `quarkus-junit5`, `mockito-core`, `assertj-core`, `awaitility`

### ChatModelAgentProvider (ChatModel → AgentProvider)

CDI bean: `@Alternative @Priority(1) @ApplicationScoped`

**`invoke(AgentSessionConfig)`:**
- Extracts `systemPrompt` + `userPrompt` from config
- Builds `ChatRequest` with `SystemMessage` + `UserMessage`
- If backing model is `StreamingChatModel`: stream via handler, emit `TextDelta` into `Multi`
- If backing model is `ChatModel` only: call `doChat()`, emit single `TextDelta`, complete
- `config.timeout()` enforced via `Multi.ifNoItem().after(timeout).fail(AgentTimeoutException)`
- `config.mcpServers()` ignored; log warning if non-empty
- `config.correlationId()` passed through for caller tracking

**`openSession(AgentSessionInit)`:**
- Creates internal `MessageWindowChatMemory` (LangChain4j built-in)
- Returns `ChatModelAgentSession` implementing `AgentSession`:
  - `query(prompt)`: add UserMessage to memory, call model with full history, add AiMessage,
    stream TextDelta events
  - `interrupt()`: cancel in-flight streaming (best-effort). No-op for synchronous ChatModel.
  - `close(Duration)`: clear memory. No subprocess to drain. The timeout parameter exists for
    waiting on in-flight blocking `ChatModel.doChat()` calls, not subprocess drain.
- Serial model enforced: `IllegalStateException` on concurrent `query()`

**Tool calls:** Opaque. If the ChatModel has tools configured via LangChain4j, the model handles
them internally. Consumer sees only text — consistent with Claude CLI's opaque model.

**CDI injection and graceful deactivation:**

Uses `@Inject @Any Instance<ChatModel>` with `@PostConstruct` that:
1. Selects `@Default`-qualified beans via `instance.select(Default.Literal.INSTANCE)`
2. Filters out `AgentProviderChatModel` (prevents circular dependency)
3. If a real ChatModel exists: stores a reference, bean is active
4. If no real ChatModel exists: logs a warning and sets `disabled = true`

When disabled, `invoke()` returns a failed `Multi` with `IllegalStateException` and a message
directing the deployer to add a `quarkus-langchain4j-*` provider. `openSession()` throws the
same. The bean is always safe to instantiate — it's only unsafe to use without a backing model.

This prevents failures in deployments where `agent-langchain4j` is on the classpath alongside
`agent-claude` but without a standalone LangChain4j model provider (the deployer wants only
the `AgentProviderChatModel` direction).

### AgentProviderChatModel (AgentProvider → ChatModel)

CDI bean: `@DefaultBean @Priority(10) @ApplicationScoped`, implements `ChatModel` +
`StreamingChatModel`.

Generic replacement for both `ClaudeAgentChatModel` and `OpenClawChatModel`.

**Why `@DefaultBean` not `@Alternative`:** quarkus-langchain4j registers its `ChatModel` beans
via `SyntheticBeanBuildItem.defaultBean()`. An `@Alternative` would suppress `@DefaultBean`
entirely — the raw ChatModel would vanish from the container, breaking `ChatModelAgentProvider`'s
`Instance<ChatModel>` lookup. `@DefaultBean @Priority(10)` coexists with the raw ChatModel: both
are visible in `Instance`, and `@Priority(10)` wins for direct `@Inject ChatModel` injection.
A consumer-provided non-`@DefaultBean` ChatModel properly overrides both defaults.

**Design constraint — restricted ChatModel adapter:**

AgentProviderChatModel intentionally does not implement the full `ChatModel` contract.
AgentProvider owns session history — passing `AiMessage` from outside is a caller error because
it implies the caller is managing conversation state that the agent manages opaquely.

Supported patterns:
- Single-shot engine calls with `SystemMessage` + `UserMessage`
- Any caller that passes only new messages per call (no history replay)

Unsupported patterns:
- LangChain4j `AI Services` with external `ChatMemory` (passes full history including AiMessage)
- Any client that manages message history and expects the model to see prior exchanges

This restriction is documented in `agent-api/README.md` (issue #114). The adapter throws
`IllegalArgumentException` on `AiMessage` with a message explaining why. Multiple `UserMessage`
elements also throw — each call must supply exactly one new message.

**`doChat(ChatRequest)` (blocking):**
- Extract system prompt via `SystemMessage.findFirst()`
- Extract last user text (reject `AiMessage` — session history is opaque)
- JSON format handling: if `ResponseFormatType.JSON` with schema, prompt-engineer the schema into
  the user text (following OpenClaw's `prependSchema` approach). No hard rejection.
- Build `AgentSessionConfig`, call `agentProvider.invoke()`, filter `TextDelta`, join, return
  `ChatResponse`

**Design improvement — uses `invoke()` not `openSession()` for single-shot calls:**

The existing `ClaudeAgentChatModel` uses `openSession()` + single `query()` + `close()` for
every `doChat()` call. The prior spec (2026-06-16) documented the rationale: `openSession()`
throws `AgentSessionLimitException` synchronously, while `invoke()` wraps it in a failed `Multi`.
In practice, both paths surface the exception clearly — `Multi.createFrom().failure()` is
immediate, and `await().indefinitely()` unwraps it directly. The ergonomic advantage of
synchronous exception is marginal. `invoke()` is the semantically correct API for single-shot
calls, and the new adapter uses it.

**`doChat(ChatRequest, StreamingChatResponseHandler)` (streaming):**
- Same extraction, subscribe to `Multi`:
  - `TextDelta` → `handler.onPartialResponse(delta.text())`
  - `onFailure` → `handler.onError()`
  - `onCompletion` → `handler.onCompleteResponse()`

**`provider()`:** Returns `ModelProvider.OTHER`. The adapter doesn't know the underlying
provider identity — `AgentProvider` has no `provider()` method. This changes the behavior from
`ClaudeAgentChatModel` (which returned `ModelProvider.ANTHROPIC`). An honest answer for a
generic adapter.

**`listeners()`:** Injects `@Any Instance<ChatModelListener>` for observability.

**CDI @DefaultBean @Priority(10) consequence:**

At `@DefaultBean @Priority(10)`, `AgentProviderChatModel` beats `@DefaultBean @Priority(0)`
ChatModel beans (from quarkus-langchain4j) for `@Inject ChatModel` injection. This is intentional
— engine code goes through AgentProvider by default. A consumer who provides their own
non-`@DefaultBean` ChatModel automatically overrides both defaults — no qualifiers needed.
Callers in two-default deployments who need the raw model should use `@ModelName` or a custom
qualifier.

**Listener double-firing in two-hop deployments:**

In a LangChain4j-only deployment where both adapters are active:

`@Inject ChatModel → AgentProviderChatModel → ChatModelAgentProvider → raw ChatModel`

The outer `AgentProviderChatModel` fires its injected listeners. The inner raw `ChatModel` fires
its own. If the same `ChatModelListener` bean appears in both listener lists, it fires twice.
This is the natural consequence of two `ChatModel` layers — each layer legitimately observes its
own contract. Documented as known behavior; deduplication would add fragile coupling between
layers.

### AgentSessionChatModel (moved from agent-claude-langchain4j)

Plain class (not CDI). Wraps a caller-supplied `AgentSession` as `ChatModel` +
`StreamingChatModel`. Moved unchanged except for JSON format handling — prompt engineering
replaces the hard `UnsupportedFeatureException`.

### ChatModelAgentSession

Plain class implementing `AgentSession`. Wraps `ChatModel` + `MessageWindowChatMemory` for
multi-turn.

- IDLE/ACTIVE/CLOSED state machine (matches `ClaudeAgentSession` contract)
- `AtomicReference<State>` for thread-safe state transitions
- `query()` on CLOSED → `IllegalStateException`
- Concurrent `query()` → `IllegalStateException`

### AgentLangchain4jProperties

```java
@ConfigMapping(prefix = "casehub.platform.agent.langchain4j")
public interface AgentLangchain4jProperties {
    @WithDefault("PT30S")
    Duration closeTimeout();

    @WithDefault("20")
    int sessionMemoryWindowSize();
}
```

### CDI circular dependency prevention

Both `ChatModelAgentProvider` and `AgentProviderChatModel` exist in the same module.

- `AgentProviderChatModel` directly `@Inject`s `AgentProvider`
- `ChatModelAgentProvider` uses `Instance<ChatModel>` (dynamic lookup), selects `@Default`-
  qualified beans, and filters out `AgentProviderChatModel` at `@PostConstruct`

ArC doesn't trace through `Instance` for cycle detection → no build-time error. Runtime filtering
ensures only "real" ChatModel beans (from quarkus-langchain4j providers) are wrapped. If no real
ChatModel exists, the bean deactivates gracefully (see ChatModelAgentProvider section).

### CDI tier structure

**AgentProvider tiers:**

| Tier | Bean | Annotation | Activates when |
|------|------|------------|----------------|
| 0 | `NoOpAgentProvider` | `@DefaultBean` | Nothing else on classpath |
| 1 | `ChatModelAgentProvider` | `@Alternative @Priority(1)` | LangChain4j ChatModel available |
| 10 | `ClaudeAgentProvider` | `@Alternative @Priority(10)` | Claude on classpath (always wins) |

**ChatModel tiers (separate — `@DefaultBean` system, not `@Alternative`):**

| Bean | Annotation | When selected |
|------|------------|---------------|
| quarkus-langchain4j ChatModel | `@DefaultBean` (implicit `@Priority(0)`) | Only non-platform ChatModel on classpath |
| `AgentProviderChatModel` | `@DefaultBean @Priority(10)` | Beats raw ChatModel when both exist; suppressed by consumer-provided non-`@DefaultBean` |

### Files

| File | Purpose |
|------|---------|
| `ChatModelAgentProvider.java` | ChatModel → AgentProvider CDI bean |
| `ChatModelAgentSession.java` | Multi-turn session backed by ChatModel + ChatMemory |
| `AgentProviderChatModel.java` | AgentProvider → ChatModel CDI bean |
| `AgentSessionChatModel.java` | AgentSession → ChatModel plain class (moved) |
| `AgentLangchain4jProperties.java` | Config mapping |
| Tests for each production class | |

---

## Impact on existing modules

### agent-claude/ClaudeAgentProvider

Changes from `@ApplicationScoped` to `@Alternative @Priority(10) @ApplicationScoped`. Functionally
identical — still beats `@DefaultBean`, now participates in the `@Alternative` tier system.

### agent-claude-langchain4j/ — deleted

- `ClaudeAgentChatModel` → replaced by `AgentProviderChatModel` in `agent-langchain4j/`
- `AgentSessionChatModel` → moved to `agent-langchain4j/`
- `ClaudeAgentLangchain4jProperties` → replaced by `AgentLangchain4jProperties`

Consumer dependency change: `casehub-platform-agent-claude-langchain4j` →
`casehub-platform-agent-langchain4j`. Issues filed against affected consumer repos.

### platform/NoOpAgentProvider

Log message updated to mention both options:

> "NoOpAgentProvider is active — add casehub-platform-agent-claude (native Claude) or
> casehub-platform-agent-langchain4j (any LangChain4j model) to the classpath"

### Parent POM

- Remove `<module>agent-claude-langchain4j</module>`
- Add `<module>agent-langchain4j</module>`

### CLAUDE.md module table

Update to reflect new module, removed module, and changed annotations.

### ARC42STORIES.MD L8

Update L8 layer taxonomy row from:

> L8: Agent Infrastructure | `agent-api/`, `agent-claude/`, `agent-claude-langchain4j/`

to:

> L8: Agent Infrastructure | `agent-api/`, `agent-claude/`, `agent-langchain4j/`

---

## Design notes

**JSON schema prompt engineering:** `AgentProviderChatModel` converts `ResponseFormatType.JSON`
with schema into a prompt prefix, following OpenClaw's `serializeSchema()` approach: enumerate
properties with types and required markers, then append the user text. This works universally
across all AgentProvider backends (Claude CLI, OpenClaw, LangChain4j-backed).

**Multiple ChatModel beans:** `ChatModelAgentProvider` selects `@Default`-qualified `ChatModel`
beans via `instance.select(Default.Literal.INSTANCE)`, then filters via `.stream().filter()`
to exclude `AgentProviderChatModel`. For direct `@Inject ChatModel`, CDI fails with
`AmbiguousResolutionException` if multiple `@Default` beans exist — the right behavior. The
`Instance`-based lookup in `ChatModelAgentProvider` uses streaming, so it never triggers
`AmbiguousResolutionException` itself — it selects the first non-adapter bean.

**Two-hop JSON mode degradation:** In a LangChain4j-only deployment, the two-hop chain
(`AgentProviderChatModel` → `ChatModelAgentProvider` → raw ChatModel) prompt-engineers JSON
schemas at the outer layer. The inner raw ChatModel receives plain text — native JSON mode is
lost even if the underlying model supports structured output natively. This is an accepted
trade-off: AgentProvider has no JSON mode concept, so prompt engineering is the universal
fallback.

---

## Deferred

- **AgentEvent extension** (#118) — ToolCall, ToolResult, ThinkingDelta variants. Kept as
  `TextDelta` only for now. Tool calls remain opaque. Extension is additive (sealed interface,
  new permits).
- **Concurrency limits for ChatModelAgentProvider** — `ClaudeAgentProvider` uses a semaphore
  (`maxConcurrentSessions`). `ChatModelAgentProvider` has none in v1. A configurable semaphore
  for cost control and backpressure on LLM API calls should be added as a follow-up.
- **Native OpenAI/Gemini AgentProvider** — if a future provider's native SDK offers advantages
  LangChain4j can't express, a new `@Alternative @Priority(10)` implementation can be added
  alongside Claude's.
