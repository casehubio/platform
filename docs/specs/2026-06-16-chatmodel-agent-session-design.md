# Design: ChatModel Adapter Backed by AgentSession

**Issue:** casehubio/platform#100  
**Date:** 2026-06-16  
**Branch:** issue-100-chatmodel-agent-session

---

## Context

`casehub-eidos` and future consumers need single-turn LLM inference with large system prompts. The
existing `quarkus-langchain4j-anthropic` integration does not support `cache_control` breakpoints
(langchain4j#1591), so every call pays full token cost. The `ClaudeAgentProvider` subprocess model
manages prompt caching automatically via the Claude CLI.

**Caching mechanism — honest statement:** `CLIOptions.systemPrompt` is passed as a plain string to
the Claude CLI binary. The Java SDK (`claude-code-sdk`) does not add `cache_control` breakpoints —
the CLI binary itself manages prompt caching transparently when communicating with Anthropic's API.
This is documented Claude CLI behavior and the basis of the cost-reduction claim, but it is not
verifiable from the Java SDK layer.

This module bridges LangChain4j's `ChatModel` / `StreamingChatModel` interfaces to the platform's
`AgentSession` SPI, exposing two paths with different session lifetime strategies.

---

## Architectural Boundary — Not For Use With `engine.Agent`

**`casehub-engine`'s `Agent` class always forces `ResponseFormatType.JSON`** (hardcoded in
`Agent.buildResponseFormat()`). Every call through the engine's `Agent` pattern would throw
`UnsupportedFeatureException` from `validateNoJsonFormat()` when this adapter is active.

This is intentional. These are two different capability tiers:

- **`engine.Agent`** — structured JSON output with an output transformer and response schema.
  Requires JSON mode. Use a JSON-capable `ChatModel` (e.g. `quarkus-langchain4j-anthropic`).
- **This adapter** — text/conversational inference with prompt caching. No JSON mode. Intended for
  direct `ChatModel.chat(request)` calls where the caller controls the message contract.

Callers using this adapter must not use it through `engine.Agent`. The hard
`UnsupportedFeatureException` on JSON format is correct and intentional — it surfaces the mismatch
immediately rather than silently producing unparseable output.

---

## Two Entry Points

### Stateless path — `ClaudeAgentChatModel`

Fresh `AgentSession` per `doChat()` call. The subprocess is created, queried once, and closed.
System prompt caching operates at the Anthropic API level via the CLI binary.

Use this when each `doChat()` call is semantically independent (same large system prompt, different
user queries). The adapter is `@ApplicationScoped` — one CDI bean, many independent calls.

**Why `openSession()` rather than `invoke()`:** `openSession()` throws `AgentSessionLimitException`
**synchronously** before any `Multi` is returned. In the blocking path this propagates cleanly
before `await()`; in the streaming path it is caught and routed to `handler.onError()` without
needing to subscribe to a `Multi` at all. `invoke()` emits the limit exception via `onFailure()`,
which complicates both paths without any benefit.

### Multi-turn path — `AgentSessionChatModel`

Wraps a caller-supplied `AgentSession`. Each `doChat()` sends only the last `UserMessage`; the
subprocess accumulates conversation history internally. The caller owns the session lifecycle.

Do NOT use LangChain4j `ChatMemory` with this path — the subprocess IS the memory.

**Session lifetime — caller responsibility (no idle timeout).** Forgetting `close()` leaks a
semaphore slot permanently. An idle timeout can be added as a decorator if needed.

Typical caller pattern:
```java
try (AgentSession s = agentProvider.openSession(AgentSessionInit.of(systemPrompt))) {
    AgentSessionChatModel model = AgentSessionChatModel.wrap(s);
    // multi-turn doChat() calls...
}
```

`AgentSessionChatModel.wrap(AgentSession)` is a static factory returning `AgentSessionChatModel`
(not `ChatModel`) — the concrete type gives callers access to both `ChatModel` and
`StreamingChatModel` without a cast.

---

## Module

**Folder:** `agent-claude-langchain4j/`  
**Artifact:** `casehub-platform-agent-claude-langchain4j`  
**Package:** `io.casehub.platform.agent.langchain4j`

No `quarkus:build` goal — library module. Jandex index required.

**Config interface:**
```java
@ConfigMapping(prefix = "casehub.platform.agent.langchain4j")
public interface ClaudeAgentLangchain4jProperties {
    @WithDefault("PT30S")
    Duration closeTimeout();
}
```

**Dependencies:**
| Artifact | Scope | Why |
|----------|-------|-----|
| `casehub-platform-agent-api` | compile | `AgentProvider`, `AgentSession`, `AgentSessionInit`, `AgentEvent` |
| `langchain4j-core` | compile | `ChatModel`, `StreamingChatModel`, `ChatRequest`, `ChatResponse`, `ModelProvider`, message types |
| `quarkus-arc` | compile | CDI annotations and `Instance<>` for `ClaudeAgentChatModel` |
| `quarkus-junit5` | test | |
| `mockito-core` | test | `mock(Instance.class)`, `mock(ChatModelListener.class)`, `verify()` in `ClaudeAgentChatModelTest` |
| `assertj-core` | test | |
| `awaitility` | test | |

No dependency on `casehub-platform-agent-claude`. The adapter injects `AgentProvider` (SPI).

---

## `ClaudeAgentChatModel`

```
@Alternative @Priority(10) @ApplicationScoped
public class ClaudeAgentChatModel implements ChatModel, StreamingChatModel
```

**CDI activation:** `@Alternative @Priority(10)` wins over `@DefaultBean` and plain
`@ApplicationScoped`. One bean satisfies both `ChatModel` and `StreamingChatModel` injection points.

**Constructor (follows `ClaudeAgentClient` pattern — constructor injection throughout):**
```java
@Inject
ClaudeAgentChatModel(AgentProvider agentProvider,
                     @Any Instance<ChatModelListener> listeners,
                     ClaudeAgentLangchain4jProperties properties) { ... }

/** For ARC proxy generation — must not be called directly. */
protected ClaudeAgentChatModel() {
    this.agentProvider = null; this.listeners = null; this.properties = null;
}
```

Tests construct directly with a fake `AgentProvider`, a stub `Instance<ChatModelListener>` (e.g.
Mockito mock with `when(stub.stream()).thenReturn(Stream.empty())`), and test properties.

**Required overrides:**

Both `ChatModel` and `StreamingChatModel` declare `listeners()`, `defaultRequestParameters()`,
`provider()`, and `supportedCapabilities()` as default methods with identical signatures. Java
requires explicit overrides in any class implementing both to resolve the diamond ambiguity —
omitting any of these produces a compile error.

```java
@Override public ModelProvider provider() { return ModelProvider.ANTHROPIC; }

@Override public Set<Capability> supportedCapabilities() { return Set.of(); }

@Override
public ChatRequestParameters defaultRequestParameters() {
    return DefaultChatRequestParameters.EMPTY;
}

@Override
public List<ChatModelListener> listeners() {
    return injectedListeners.stream().toList();
    // Without this, provider() is meaningless — ChatModel.chat() calls onRequest/onResponse/onError
    // with an empty listener list, producing no telemetry.
}
```

**Blocking `doChat(ChatRequest)` → `ChatResponse`:**
1. `validateNoJsonFormat(request)` — private static; throws `UnsupportedFeatureException` if
   `request.responseFormat()` is non-null and type is JSON
2. Extract system prompt: `SystemMessage.findFirst(messages).map(SystemMessage::text).orElse("")`
3. `extractLastUserText(messages)` — private static; see validation rules below
4. `agentProvider.openSession(AgentSessionInit.of(systemPrompt))` — throws
   `AgentSessionLimitException` synchronously if cap exceeded
5. `session.query(userMessage)` → filter `TextDelta` → map to text → `collect().with(joining())`
   → `await().indefinitely()`  
   _(The `TextDelta` filter is always true today — `AgentEvent` is a sealed interface with only
   `TextDelta`. Retained as a forward-compatibility guard in case `AgentEvent` is extended.)_  
   _(Wall-clock bound: session-level timeout fires as `AgentTimeoutException` via the Multi and
   is rethrown by `await()` — this call cannot hang indefinitely.)_
6. Build `ChatResponse` with `AiMessage.from(text)`, `FinishReason.STOP`, null `TokenUsage`
7. `finally: session.close(properties.closeTimeout())`

**Streaming `doChat(ChatRequest, StreamingChatResponseHandler)`:**  
_(Import: `dev.langchain4j.model.chat.response.StreamingChatResponseHandler`)_

**Propagation note — three distinct paths:**
- Steps 1–3 throw synchronously from `doChat()` (programming errors — propagate from `chat()`)
- `AgentSessionLimitException` (step 4) is operational — caught, routed to `handler.onError()`
- Failures from `query()` via Multi go to `handler.onError()`

Steps:
1. `validateNoJsonFormat(request)` (synchronous throw)
2. Extract system prompt (synchronous)
3. `extractLastUserText(messages)` (synchronous throw on violation)
4. `openSession()` — catch `AgentSessionLimitException` → `handler.onError()`, return
5. Local `StringBuilder buffer` (call frame — Mutiny serial, no synchronization needed)
6. `session.query(userMessage).subscribe().with(`:
   - `onItem`: if `TextDelta`, `handler.onPartialResponse(delta.text())` + append to buffer
   - `onFailure`: `session.close(properties.closeTimeout())`, then `handler.onError(error)`
   - `onCompletion`: `session.close(properties.closeTimeout())`, then
     `handler.onCompleteResponse(ChatResponse with AiMessage.from(buffer.toString()),
     FinishReason.STOP, null TokenUsage)`

Session close is in Mutiny terminal handlers, not `try-with-resources`.

**NoOp behaviour:** When `agent-claude` is not on the classpath, `NoOpAgentProvider` is active.
`NoOpAgentProvider.openSession()` emits `LOG.warn(...)` on every call — this is the primary
misconfiguration signal. `NoOpAgentSession.query()` returns `Multi.createFrom().empty()`, so
`doChat()` returns `ChatResponse(AiMessage.from(""))`.

---

## `AgentSessionChatModel`

```
public final class AgentSessionChatModel implements ChatModel, StreamingChatModel
```

No CDI. Constructor: `AgentSessionChatModel(AgentSession session)`.  
Static factory: `static AgentSessionChatModel wrap(AgentSession session)` returning
`AgentSessionChatModel`.

Overrides `provider()` → `ModelProvider.ANTHROPIC`, `supportedCapabilities()` → `Set.of()`,
`defaultRequestParameters()` → `DefaultChatRequestParameters.EMPTY`, and `listeners()` →
`List.of()`. All four overrides are required to resolve the `ChatModel`/`StreamingChatModel`
diamond (see "Required overrides" note in `ClaudeAgentChatModel`).

`listeners()` returns `List.of()` — no CDI injection available. The telemetry infrastructure
runs against an empty listener list, producing no output. V1 gap; a future version can accept
`List<ChatModelListener>` as a constructor parameter.

**Blocking `doChat(ChatRequest)` → `ChatResponse`:**
1. `validateNoJsonFormat(request)` (private static)
2. `extractLastUserText(messages)` (private static)
3. `session.query(userMessage)` → same filter-map-collect-`await().indefinitely()` chain as
   `ClaudeAgentChatModel` step 5; the sealed-interface note applies equally. If the session is
   CLOSED or already-active, `session.query()` throws `IllegalStateException` synchronously here —
   `await()` is never reached.
4. Build `ChatResponse` with `AiMessage.from(text)`, `FinishReason.STOP`, null `TokenUsage`

Session NOT closed — caller owns lifecycle.

**Streaming `doChat(ChatRequest, StreamingChatResponseHandler)`:**

Steps 1–2 throw synchronously from `doChat()` — same propagation rule as `ClaudeAgentChatModel`.
No `openSession()` step.

Positive steps:
1. `validateNoJsonFormat(request)` (synchronous throw)
2. `extractLastUserText(messages)` (synchronous throw on violation)
3. Local `StringBuilder buffer` (call frame — Mutiny serial, no synchronization needed)
4. `session.query(userMessage).subscribe().with(`:
   - `onItem`: if `TextDelta`, `handler.onPartialResponse(delta.text())` + append to buffer
   - `onFailure`: `handler.onError(error)` — **no** `session.close()`, caller owns lifecycle
   - `onCompletion`: `handler.onCompleteResponse(ChatResponse with AiMessage.from(buffer.toString()),
     FinishReason.STOP, null TokenUsage)` — **no** `session.close()`

`AgentSessionChatModel` is SPI-agnostic and never calls `close()` regardless of whether the
underlying session self-closes on error.

**Multi-turn contract:** each `doChat()` call must supply a `ChatRequest` whose only message is the
new user turn. Starting a new conversation requires a new `AgentSession`.

---

## Private Static Helpers

**`ClaudeAgentChatModel`** — three helpers:
```java
// Uses SystemMessage.findFirst() — LangChain4j's own idiom
private static String extractSystemPrompt(List<ChatMessage> messages) {
    return SystemMessage.findFirst(messages).map(SystemMessage::text).orElse("");
}

private static String extractLastUserText(List<ChatMessage> messages) { ... }  // see below

private static void validateNoJsonFormat(ChatRequest request) { ... }  // see below
```

**`AgentSessionChatModel`** — two helpers (no `extractSystemPrompt` — system prompt was set at
session-open time and lives in the subprocess; re-reading it from the message list would be
dead code):
```java
private static String extractLastUserText(List<ChatMessage> messages) { ... }
private static void validateNoJsonFormat(ChatRequest request) { ... }
```

**`extractLastUserText` validation (in order):**
1. Throws `IllegalArgumentException` if any `AiMessage` is present:  
   `"AgentSession-backed ChatModel adapters do not accept AiMessage — session history lives in the subprocess. Each call must supply only a single new UserMessage."`
2. Throws `IllegalArgumentException` if more than one `UserMessage` is present:  
   `"AgentSession-backed ChatModel adapters accept exactly one UserMessage per call — multiple UserMessage elements indicate caller confusion about session state."`
3. Throws `IllegalArgumentException` if no `UserMessage` present
4. Calls `singleText()` on the single `UserMessage`. If multimodal, `singleText()` throws
   `RuntimeException("Expecting single text content, but got: [...])` — catch and rethrow as
   `IllegalArgumentException("This adapter supports text-only UserMessage; multimodal content is not supported by the subprocess.")`
5. Returns the text

**`validateNoJsonFormat`:**  
Throws `dev.langchain4j.exception.UnsupportedFeatureException` if `request.responseFormat()` is
non-null and `responseFormat().type() == ResponseFormatType.JSON`. Uses `request.responseFormat()`
direct accessor (delegates to `parameters().responseFormat()`).

---

## Error Surface

| Error | Origin | Blocking | Streaming — thrown from `doChat()` | Streaming — `handler.onError()` |
|-------|--------|----------|-------------------------------------|----------------------------------|
| `UnsupportedFeatureException` | JSON format (step 1) | thrown | ✓ | — |
| `IllegalArgumentException` | AiMessage / multiple UserMessage / no UserMessage / multimodal | thrown | ✓ | — |
| `IllegalStateException` (CLOSED or already-active session) | `query()` synchronous CAS check | thrown | ✓ | — |
| `AgentSessionLimitException` | `openSession()` (stateless only) | thrown | — | ✓ (caught, routed) |
| `AgentTimeoutException` | `query()` via Multi | rethrown by `await()` | — | ✓ |
| `AgentProcessException` | `query()` via Multi | rethrown by `await()` | — | ✓ |

**Note on `IllegalStateException`:** `ClaudeAgentSession.query()` and `NoOpAgentSession.query()`
throw `IllegalStateException` synchronously (before returning any `Multi`) from the same CAS
check at step 2 of `query()` — two distinct messages:
- `"session is closed"` when state is CLOSED
- `"a turn is already active — wait for it to complete or call interrupt()"` when state is ACTIVE
  (happens when a second `doChat()` is called while a streaming response is still in progress)

Both propagate synchronously from `doChat()` to the caller of `chat()` — not to `handler.onError()`.

**Crash recovery:** once a session goes CLOSED via error, the next `query()` call throws
`IllegalStateException` synchronously. The caller must open a new session.

---

## Testing

### `ClaudeAgentChatModelTest` (plain JUnit5, no Quarkus)

Use the test constructor with a fake `AgentProvider` (anonymous class), a Mockito mock
`Instance<ChatModelListener>` (`when(stub.stream()).thenReturn(Stream.empty())`), and a
test `ClaudeAgentLangchain4jProperties`.

Tests:
- Happy path blocking: `aiMessage().text()` correct, `finishReason() == STOP`
- Happy path streaming: `onPartialResponse` per `TextDelta`, `onCompleteResponse` with accumulated
  text and `finishReason() == STOP`
- Multi-delta accumulation: text concatenated in order
- `AgentTimeoutException` propagates from blocking; routed to `handler.onError()` in streaming
- `AgentProcessException` — same
- `AgentSessionLimitException` (streaming) → `handler.onError()`, no subscription
- `UnsupportedFeatureException` on JSON format — thrown from `doChat()` (synchronous)
- `IllegalArgumentException` on no `UserMessage` (synchronous)
- `IllegalArgumentException` on `AiMessage` present (synchronous)
- `IllegalArgumentException` on multiple `UserMessage` (synchronous)
- `IllegalArgumentException` on multimodal `UserMessage` (synchronous)
- `IllegalStateException` (CLOSED or already-active session, streaming) — thrown synchronously
  from `doChat()` (the CAS check in `query()` fires before any `Multi` is returned)
- `provider()` returns `ModelProvider.ANTHROPIC`
- `supportedCapabilities()` returns empty set
- Listener scenario A — empty stub: `listeners()` returns `List.of()`, no listener methods called
- Listener scenario B — mock listener injected via stub `Instance`: after `chat()` completes,
  verify `mockListener.onRequest()` and `mockListener.onResponse()` were each called once
- Session `close()` called in `finally` after successful blocking
- Session `close()` called in streaming `onFailure` and `onCompletion`

### `AgentSessionChatModelTest` (plain JUnit5)

Fake `AgentSession` (anonymous class with `Function<String, Multi<AgentEvent>>` turn factory).

Tests:
- Happy path blocking: correct text, `finishReason() == STOP`
- Happy path streaming: partial responses, `onCompleteResponse` with `finishReason() == STOP`
- Multi-turn: two sequential `doChat()` — correct single user message sent per turn
- `IllegalArgumentException` on `AiMessage` (synchronous)
- `IllegalArgumentException` on multiple `UserMessage` (synchronous)
- `IllegalArgumentException` on multimodal `UserMessage` (synchronous)
- `IllegalStateException` (CLOSED session, blocking) — thrown synchronously from `session.query()`
  at step 3; `await()` is never reached
- `IllegalStateException` (CLOSED session, streaming) — thrown synchronously from `doChat()`
- `IllegalStateException` (already-active session, streaming) — second concurrent `doChat()` call
  while a streaming response is in progress; thrown synchronously from `doChat()`
- Adapter does NOT call `session.close()` after completion or `onFailure`
- `AgentTimeoutException` propagates from blocking / routes to `handler.onError()` in streaming
- `provider()` returns `ModelProvider.ANTHROPIC`
- `supportedCapabilities()` returns empty set
- `listeners()` returns `List.of()` (v1 gap documented)
- `wrap()` factory returns `AgentSessionChatModel` instance

---

## Constraints Carried Forward

- **JSON format / `engine.Agent`:** incompatible with `engine.Agent` which forces JSON. Use
  `quarkus-langchain4j-anthropic` for structured JSON output.
- **`TokenUsage`:** null — subprocess does not expose token counts.
- **`FinishReason`:** `STOP` for all normal completions.
- **Crash recovery:** after session goes CLOSED (error), `query()` throws `IllegalStateException`
  synchronously. Caller must open a new session.
- **Threading:** `ChatModel.doChat()` blocks calling thread — must not be called from the Vert.x
  event loop. Streaming `doChat()` returns immediately; callbacks fire on the Mutiny worker pool.
- **Tool calls:** only `TextDelta` observable. Claude Code tool invocations are opaque.
- **Multimodal input:** not supported. `IllegalArgumentException` on multimodal `UserMessage`.
- **Caching:** managed entirely by the Claude CLI binary, not by the Java SDK.
- **`listeners()` on `AgentSessionChatModel`:** v1 gap — `List.of()`, telemetry disabled.

---

## PLATFORM.md / CLAUDE.md Updates Required

- Add `agent-claude-langchain4j/` to the module table in `CLAUDE.md` and to the casehub-platform
  repo row in `PLATFORM.md`
- Add `casehub-platform-agent-claude-langchain4j` to the Cross-Repo Dependency Map when a
  consumer (e.g. `casehub-eidos`) adds it
