# ChatModelAgentProvider StreamingChatModel Upgrade

**Issue:** casehubio/platform#120
**Date:** 2026-06-29
**Status:** Approved

## Problem

`ChatModelAgentProvider.invoke()` calls blocking `chatModel.chat(request)` and wraps
the complete response as a single `TextDelta`. But most Quarkus LangChain4j providers
(OpenAI, Anthropic, Ollama) implement both `ChatModel` and `StreamingChatModel` on one
bean. Without using the streaming path, the LangChain4j→AgentProvider direction can
never emit `ThinkingDelta`, `ToolCallDelta`, `ToolCallComplete`, or `ToolResult` —
leaving LangChain4j-backed agents permanently limited to single-TextDelta responses.

Same limitation affects `ChatModelAgentSession.query()`.

## Approach: AgentEventBridge + capability detection

Single utility class with two static methods for the bidirectional mapping between
`AgentEvent` and `StreamingChatResponseHandler`. `ChatModelAgentProvider` detects
`instanceof StreamingChatModel` at init time and branches accordingly.

This also consolidates the existing duplicated AgentEvent↔handler mapping code in
`AgentProviderChatModel` and `AgentSessionChatModel` into the same utility.

## Design

### AgentEventBridge

New package-private utility in `agent-langchain4j`. No state, no lifecycle.

**`stream(StreamingChatModel, ChatRequest) → Multi<AgentEvent>`** — handler→AgentEvent:

- Calls `chat()` (not `doChat()`) — this is the full LangChain4j lifecycle entry point that
  applies `defaultRequestParameters().overrideWith(request.parameters())` and fires
  `ChatModelListener` callbacks (`onRequest`/`onResponse`/`onError`). Calling `doChat()`
  would silently bypass parameter defaults and listener firing. (GE-20260529-0c80ca about
  mocking `doChat()` applies to test setup only — tests mock the implementation hook while
  letting `chat()` drive the real lifecycle.)
- Uses `Multi.createFrom().emitter()` (correct for push-stream; GE-20260529-0b8284 warns
  against `Uni.createFrom().emitter()` for one-shot, but `Multi` is the right choice here)
- Handler implements both context-aware and simple overloads:
  - `onPartialResponse(PartialResponse, PartialResponseContext)` → `TextDelta` (captures `StreamingHandle`)
  - `onPartialResponse(String)` → `TextDelta` (fallback for providers that call only the simple overload)
  - `onPartialThinking(PartialThinking, PartialThinkingContext)` → `ThinkingDelta` (captures `StreamingHandle`)
  - `onPartialThinking(PartialThinking)` → `ThinkingDelta` (fallback)
  - `onPartialToolCall(PartialToolCall, PartialToolCallContext)` → `ToolCallDelta` (captures `StreamingHandle`)
  - `onPartialToolCall(PartialToolCall)` → `ToolCallDelta` (fallback)
  - `onCompleteToolCall(CompleteToolCall)` → `ToolCallComplete` — note the nested field
    extraction: `completeToolCall.toolExecutionRequest().id()`, `.name()`, `.arguments()`
    (unlike `PartialToolCall` which has flat fields)
  - `onCompleteResponse(ChatResponse)` → `emitter.complete()`
  - `onError(Throwable)` → `emitter.fail()`
- `ToolResult` has no handler callback — correctly absent

**Cancellation wiring:** The handler captures the `StreamingHandle` from the first context
callback via `AtomicReference.compareAndSet(null, context.streamingHandle())`. The emitter
registers `emitter.onTermination(() -> { var h = handleRef.get(); if (h != null) h.cancel(); })`
before calling `chat()`. This propagates Mutiny cancellation (timeout, downstream cancel) to
the underlying HTTP stream via `StreamingHandle.cancel()`. Without this, a cancelled subscriber
would leave the HTTP connection running until the model finishes generating.

**Provider dependency:** Cancellation propagation to the underlying HTTP stream requires the
LangChain4j provider to call context-aware overloads (e.g. `onPartialResponse(PartialResponse,
PartialResponseContext)`). Providers that call only the simple overloads (e.g.
`onPartialResponse(String)`) never supply a `StreamingHandle` — `handleRef` stays null and the
termination callback is a no-op. In that case, Mutiny cancellation still stops event processing
(the emitter is terminated and no further events are delivered to the subscriber), but the
underlying HTTP connection continues until the model finishes or the HTTP client times out.
This is a provider limitation, not a design gap — the null check in the termination callback
handles it gracefully.

**ChatResponse metadata loss:** `onCompleteResponse(ChatResponse)` maps to `emitter.complete()`,
discarding `FinishReason`, token usage, and the model-assembled `AiMessage`. This metadata has
no `AgentEvent` variant to carry it — `AgentEvent` models the streaming content, not completion
metadata. Acknowledged as a v1 limitation; a future `AgentEvent.Complete` variant could surface
this if consumers need it.

ASSUMPTION: `ToolCallDelta` validation (`name` not blank, `partialArguments` not empty) mirrors
LangChain4j's `PartialToolCall` constraints (`ensureNotBlank(name)`, `ensureNotEmpty(partialArguments)`).
If a future LangChain4j version relaxes these constraints, `ToolCallDelta` must be updated to match.

Works for both sync and async LangChain4j providers. The emitter lambda runs at
subscription time: sync providers deliver events inline; async providers push later
from the HTTP client thread.

**`dispatch(Multi<AgentEvent>, StreamingChatResponseHandler)`** — AgentEvent→handler:

- Subscribes to the Multi, dispatches via exhaustive `switch` on the sealed `AgentEvent`:
  - `TextDelta` → `handler.onPartialResponse(delta.text())` + buffer text
  - `ThinkingDelta` → `handler.onPartialThinking(new PartialThinking(thinking.text()))`
  - `ToolCallDelta` → `handler.onPartialToolCall(PartialToolCall.builder()...build())`
  - `ToolCallComplete` → `handler.onCompleteToolCall(new CompleteToolCall(...))` + collect
    `ToolExecutionRequest` into list
  - `ToolResult` → silently ignored (no handler callback)
- On completion: assembles `AiMessage` with both buffered text and collected
  `ToolExecutionRequest` list. `FinishReason` is `TOOL_EXECUTION` when tool calls are
  present, `STOP` otherwise. Uses `AiMessage.builder().text(buffer).toolExecutionRequests(reqs).build()`.
- Exhaustive `switch` ensures compile-time checking — if a future `AgentEvent` variant is
  added, the compiler flags the missing case
- Replaces the identical inline code currently duplicated in `AgentProviderChatModel`
  and `AgentSessionChatModel`

### ChatModelAgentProvider changes

**Init — streaming detection:**

```java
streamingChatModel = (chatModel instanceof StreamingChatModel s) ? s : null;
```

No separate CDI injection for `StreamingChatModel` — `instanceof` on the already-resolved
bean is correct. Separate resolution could pick a different bean or hit the circularity
filter (`AgentProviderChatModel` implements `StreamingChatModel`).

**`invoke()` — branching:**

- `ChatRequest` construction lifted above the branch (both paths need it identically)
- Streaming available: `AgentEventBridge.stream(streamingChatModel, request)`
- Blocking fallback: existing `Multi.createFrom().item()` path
- Timeout via `ifNoItem().after(timeout).failWith(AgentTimeoutException)` applied to the
  result Multi regardless of path. **Semantic change for streaming:** on the blocking path
  (single-item Multi), this is a wall-clock timeout on the entire operation. On the streaming
  path (multi-item Multi), `ifNoItem()` resets after each emitted item — it becomes an
  **inter-item inactivity timeout**. A stream that actively emits tokens will never trigger it,
  even if the total response takes longer than the timeout. This is the correct semantic for
  streaming: it detects stalled connections while allowing long-running active responses.
  A separate wall-clock timeout is not enforced on the streaming path

**`openSession()` — passes `streamingChatModel` (may be null) to session constructor.**

### ChatModelAgentSession changes

**Constructor:** accepts `StreamingChatModel streamingChatModel` (nullable).

**`query()` — streaming path:**

```java
if (streamingChatModel != null) {
    StringBuilder buffer = new StringBuilder();
    result = AgentEventBridge.stream(streamingChatModel, request)
        .onItem().invoke(event -> {
            if (event instanceof AgentEvent.TextDelta delta) {
                buffer.append(delta.text());
            }
        })
        .onCompletion().invoke(() ->
            memory.add(AiMessage.from(buffer.toString())));
}
```

- Memory updated via `onCompletion().invoke()` — only fires on normal completion
- Memory records text only (`AiMessage.from(buffer.toString())`). Tool call events are
  streamed to the consumer but not recorded in `ChatMemory`. This is intentional:
  `ChatModelAgentSession` does not execute tools — recording `ToolExecutionRequest` in
  memory without corresponding `ToolExecutionResultMessage` entries would produce invalid
  conversation history that confuses the model on subsequent turns. Tool orchestration is
  a higher-layer concern (engine or application code)
- Failure and cancellation correctly skip memory update
- State transition (`ACTIVE→IDLE`) via `.onTermination().invoke()` unchanged

### AgentProviderChatModel + AgentSessionChatModel

Both replace their `doChat(ChatRequest, StreamingChatResponseHandler)` inline mapping
with `AgentEventBridge.dispatch()`. The dispatch logic (event→handler routing) is
structurally identical, but `dispatch()` enhances completion semantics: the final
`ChatResponse` now includes `ToolExecutionRequest` objects collected from `ToolCallComplete`
events and uses `FinishReason.TOOL_EXECUTION` when tool calls are present (previously
always `STOP` with text-only `AiMessage`). For text-only streams — the current common
case — output is identical and existing tests pass unchanged.

## Testing

**AgentEventBridgeTest** (new):
- `stream()`: mock `doChat()` (per GE-20260529-0c80ca — tests mock the implementation hook,
  letting `chat()` drive the real parameter-default and listener lifecycle), verify each handler
  callback maps to the correct AgentEvent. Test mixed sequence, empty stream, error propagation.
- `stream()` mixed event interleaving: emit `TextDelta` + `ThinkingDelta` + `ToolCallDelta` +
  `ToolCallComplete` in a realistic interleaved sequence — verify all map correctly and arrive
  in order.
- `stream()` cancellation mid-stream: subscriber cancels after receiving some events — verify
  `StreamingHandle.cancel()` is called, no further events are emitted after cancellation.
- `stream()` cancellation without StreamingHandle: mock `doChat()` to call only simple overloads
  (no context-aware callbacks). Subscriber cancels mid-stream. Verify: no NPE, emitter terminates
  cleanly, events stop arriving via emitter termination (not via handle.cancel()).
- `stream()` + inactivity timeout: stream that pauses between items longer than timeout —
  verify `AgentTimeoutException` fires (documents the inactivity timeout semantic).
- `dispatch()`: verify each AgentEvent variant dispatches to correct handler method via
  exhaustive switch. ToolResult silently ignored. Completion assembles ChatResponse with
  both text and ToolExecutionRequests. FinishReason is TOOL_EXECUTION when tool calls present,
  STOP otherwise. Failure propagates.

**ChatModelAgentProviderTest** (extend):
- Streaming detection at init time (both present, ChatModel-only)
- `invoke()` uses streaming path when available, blocking when not
- `openSession()` passes streaming capability

**ChatModelAgentSessionTest** (extend):
- Streaming query emits multiple TextDeltas (not one monolithic response)
- Memory updated on completion, not on failure
- Memory does NOT record tool call events (only text)
- Memory NOT updated on streaming failure mid-way (verify no partial text recorded)
- Multi-turn with streaming preserves history

**AgentProviderChatModelTest + AgentSessionChatModelTest**: existing tests pass unchanged.

## Garden context

- GE-20260529-0b8284: `Uni.createFrom().emitter()` gotcha — does NOT apply to `Multi`
- GE-20260529-0c80ca: mock `doChat()` not `chat()` for StreamingChatModel testing
- GE-20260617-d18081: diamond problem when implementing both interfaces — does not apply
  here (we consume, not implement), but noted for awareness

## Deferred

- **Concurrency limiter** (#125) — `ChatModelAgentProvider` has no concurrency limits.
  `ClaudeAgentProvider` uses a semaphore (`maxConcurrentSessions`). With streaming, the concern
  is more acute — a streaming call holds resources for the entire token generation duration.
  Filed as #125; originally deferred from #105 (2026-06-26 spec).
- **Completion metadata** — `stream()` discards `ChatResponse` metadata (`FinishReason`, token
  usage) on completion. No `AgentEvent` variant exists to carry it. A future `AgentEvent.Complete`
  variant could surface this for observability if needed.

## Files changed

| File | Change |
|------|--------|
| `AgentEventBridge.java` | New — bidirectional mapping utility |
| `ChatModelAgentProvider.java` | Streaming detection + invoke() branching |
| `ChatModelAgentSession.java` | Streaming constructor + query() branching |
| `AgentProviderChatModel.java` | Replace inline mapping with dispatch() |
| `AgentSessionChatModel.java` | Replace inline mapping with dispatch() |
| `AgentEventBridgeTest.java` | New — both directions |
| `ChatModelAgentProviderTest.java` | Extend — streaming detection + path tests |
| `ChatModelAgentSessionTest.java` | Extend — streaming query + memory tests |
