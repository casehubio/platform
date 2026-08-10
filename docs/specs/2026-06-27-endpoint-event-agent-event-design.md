# Design: EndpointRegistered verification + AgentEvent extension

**Date:** 2026-06-27
**Issues:** #117, #118
**Branch:** issue-117-endpoint-event-agent-event

---

## Issue #117 — InMemoryEndpointRegistry event verification

### Context

`InMemoryEndpointRegistry` already fires `EndpointRegistered` via `Event<EndpointRegistered>.fireAsync()` after every successful `register()` call (lines 68-76). The implementation is correct. `NoOpEndpointRegistry` is a silent no-op — no event firing, per protocol PP-20260615-9adaee and PP-20260618-f2d154.

The existing `@QuarkusTest` (`InMemoryEndpointRegistryEventTest`) verifies CDI wiring — that `register()` completes without NPE when the CDI event bus is injected. It does not verify the event was actually fired or that it carried the correct descriptor.

### What changes

A new unit test `InMemoryEndpointRegistryFireEventTest` in `endpoints-memory/src/test/java/` that:

1. Constructs `InMemoryEndpointRegistry` with a mock `Event<EndpointRegistered>` (the existing `@Inject` constructor accepts it)
2. Calls `register()` with a known descriptor
3. Verifies `fireAsync()` was called exactly once
4. Verifies the `EndpointRegistered` event carries the same descriptor that was registered
5. Verifies a second `register()` fires a second event (upsert still fires)

### What doesn't change

- `InMemoryEndpointRegistry` — already correct
- `NoOpEndpointRegistry` — already a silent no-op
- `InMemoryEndpointRegistryEventTest` — existing CDI wiring test stays

---

## Issue #118 — AgentEvent sealed interface extension

### Architectural intent

The Agent API (`AgentProvider`, `AgentEvent`, `AgentSession`) is the platform's own abstraction for AI agent interaction. It is not tied to any single LLM backend or SDK. Implementations may use native SDKs (Claude Agent SDK, Anthropic API, Google Gemini, etc.) or bridge through LangChain4j. The SPI exists because LangChain4j's abstractions sometimes impose overhead — particularly around caching and token cost — that native SDK access avoids. LangChain4j is one adapter path, not the ceiling.

The event model captures the richest stream any reasonable LLM backend can produce. Backends that don't support a particular event type simply never emit it.

### v1 design reversal

The current `AgentEvent.TextDelta` javadoc documents two intentional exclusions:

> Absent intentionally:
> - ToolCall: Claude Code tool invocations are opaque to the observer.
> - UsageReport: SDK cost metadata out of scope for v1.

This spec reverses the ToolCall exclusion. The v1 reasoning held when the only backend was Claude CLI's `textStream()` API, where tool calls are genuinely opaque — the CLI exposes only text tokens. With `agent-langchain4j` (#105) bridging any LangChain4j `ChatModel` as an `AgentProvider`, and the architectural clarification that the Agent API is the platform abstraction (not a Claude-specific wrapper), tool calls are no longer opaque:

- LangChain4j's `StreamingChatResponseHandler` exposes `onPartialToolCall()`, `onCompleteToolCall()`, `onPartialThinking()`
- The Claude SDK's `messages()` API returns `AssistantMessage` with `ToolUseBlock`, `ToolResultBlock`, `ThinkingBlock` content blocks
- The Anthropic Messages API directly streams `content_block_start`/`content_block_delta`/`content_block_stop` for tool use

The previous spec (#105, 2026-06-26) explicitly deferred this extension: "AgentEvent extension (#118) — ToolCall, ToolResult, ThinkingDelta variants. Kept as TextDelta only for now. Tool calls remain opaque. Extension is additive (sealed interface, new permits)."

**UsageReport remains deferred** — no new event type addresses SDK cost metadata. This can be revisited when a concrete consumer need arises.

### Current state

`AgentEvent` is `sealed permits TextDelta` — the only observable event is text appearing token-by-token. Tool calls, tool results, and reasoning are invisible to observers.

### New event types

```java
public sealed interface AgentEvent permits
    AgentEvent.TextDelta,
    AgentEvent.ThinkingDelta,
    AgentEvent.ToolCallDelta,
    AgentEvent.ToolCallComplete,
    AgentEvent.ToolResult {

    record TextDelta(String text) implements AgentEvent {}
    record ThinkingDelta(String text) implements AgentEvent {}
    record ToolCallDelta(int index, String id, String name, String partialArguments) implements AgentEvent {}
    record ToolCallComplete(int index, String id, String name, String arguments) implements AgentEvent {}
    record ToolResult(String toolCallId, String content, boolean isError) implements AgentEvent {}
}
```

#### Design rationale

**ThinkingDelta** — parallel to `TextDelta`. Thinking text is independently displayable (not structured like JSON), so no "ThinkingComplete" needed. Backends that don't support extended thinking never emit it.

**ToolCallDelta vs ToolCallComplete — two records, not one.** A partial JSON fragment (`partialArguments`) and a complete parseable tool call (`arguments`) are semantically different things. Partial fragments serve progress display; complete calls serve execution and audit. Collapsing them into one record with a boolean flag pushes a type safety violation into every consumer's pattern match. Backends that deliver complete tool calls (e.g. Claude SDK via `messages()`) emit only `ToolCallComplete`, never `ToolCallDelta`.

**ToolCallDelta.index** — correlates fragments across concurrent tool calls within one stream. Matches the LangChain4j `onPartialToolCall` index semantics.

**ToolResult** — observability: what the tool returned to the agent. Correlates to `ToolCallComplete` via `toolCallId`. The observer doesn't execute tools — this is purely for audit trails, logging, and progress display. `content` is opaque text with no format guarantee — it may be valid JSON (structured tool output), plain text, an error message, or empty string. The `isError` flag indicates whether the content represents an error condition but does not constrain the content format. Observers that need structured data should parse defensively.

#### Nullability

**`ToolCallDelta.id`** — nullable. Some LLM providers (e.g. Google, Ollama) omit tool call IDs. Matches LangChain4j's `PartialToolCall.id` which documents: "some LLM providers may omit this ID."

**`ToolCallComplete.id`** — nullable, for the same reason. When null, `ToolResult` correlation falls back to ordering — each `ToolResult` corresponds to the most recent unmatched `ToolCallComplete` in the stream.

**`ToolResult.toolCallId`** — nullable. When the corresponding `ToolCallComplete.id` was null, the correlation ID is also null. Correlation by stream ordering is the fallback.

**`ToolCallDelta.name`**, **`ToolCallComplete.name`** — non-null. The tool name is always known (LangChain4j enforces `ensureNotBlank`).

**`ToolCallComplete.arguments`** — non-null. A complete tool call always has arguments (possibly `"{}"`).

All other fields — non-null.

#### Non-empty / non-blank constraints

New event types enforce non-empty/non-blank constraints via compact record constructors where the semantics require it. These are inherent domain constraints, not LangChain4j-matching:

**Non-negative (throws `IllegalArgumentException` on negative value):**
- `ToolCallDelta.index` — correlates tool call fragments; indices start at 0
- `ToolCallComplete.index` — same

**Non-empty (throws `IllegalArgumentException` on null or empty string):**
- `ThinkingDelta.text` — an empty thinking token carries no information; no backend sends them
- `ToolCallDelta.partialArguments` — an empty fragment advances nothing in JSON accumulation
- `ToolCallComplete.arguments` — a complete tool call always has arguments (at minimum `"{}"`)

**Non-blank (throws `IllegalArgumentException` on null, empty, or whitespace-only):**
- `ToolCallDelta.name` — a blank tool name is invalid at every level
- `ToolCallComplete.name` — same

**Unconstrained (non-null, empty is valid):**
- `TextDelta.text` — inherits v1 contract; some backends send empty keepalive tokens
- `ToolResult.content` — tool may return void (empty string is valid)

### Javadoc updates

- `AgentProvider` class javadoc — add architectural positioning (platform abstraction, not tied to any backend, LangChain4j is one adapter path)
- `AgentEvent` class javadoc — document the event model philosophy (richest stream any backend can produce, backends that don't support a type never emit it)
- `AgentEvent.TextDelta` javadoc — remove the "Absent intentionally" block. The exclusions it documents are either reversed (ToolCall) or captured at the interface level (UsageReport deferred)
- `AgentProvider.invoke()` return doc — broaden from "streams TextDelta items" to "streams AgentEvent items"
- `AgentSession.query()` return doc — same

### Consumer impact

**agent-langchain4j — `AgentProviderChatModel.doChat(request, handler)` (streaming):**
Currently forwards only `TextDelta → handler.onPartialResponse()`. Updated to also forward:
- `ThinkingDelta` → `handler.onPartialThinking(new PartialThinking(delta.text()))`
- `ToolCallDelta` → `handler.onPartialToolCall(PartialToolCall.builder().index(d.index()).id(d.id()).name(d.name()).partialArguments(d.partialArguments()).build())`
- `ToolCallComplete` → `handler.onCompleteToolCall(new CompleteToolCall(c.index(), ToolExecutionRequest.builder().id(c.id()).name(c.name()).arguments(c.arguments()).build()))`
- `ToolResult` — no LangChain4j `StreamingChatResponseHandler` callback for tool results; silently ignored

Note: `CompleteToolCall` wraps a `ToolExecutionRequest` — the construction is nested, not flat.

**agent-langchain4j — `AgentSessionChatModel.doChat(request, handler)` (streaming):**
Same forwarding as above.

**Blocking `doChat` methods (both classes):**
Filter on `TextDelta` — unchanged. New event types are silently filtered. Correct behaviour.

**`ChatModelAgentProvider` / `ChatModelAgentSession`:**
Emits single TextDelta per invocation via blocking `chatModel.chat()`. No change — the blocking ChatModel API does not provide streaming tool/thinking data.

**`NoOpAgentProvider` / `NoOpAgentSession`:**
Return empty Multi. No change.

### Files changed

| File | Change |
|------|--------|
| `agent-api/.../AgentEvent.java` | Extend sealed interface: 1 → 5 permits, javadoc |
| `agent-api/.../AgentProvider.java` | Class javadoc + invoke() return javadoc |
| `agent-api/.../AgentSession.java` | query() return javadoc |
| `endpoints-memory/.../InMemoryEndpointRegistryFireEventTest.java` | New: mock-based event firing verification |
| `agent-langchain4j/.../AgentProviderChatModel.java` | Streaming doChat: forward new event types to handler |
| `agent-langchain4j/.../AgentProviderChatModelTest.java` | New streaming tests: ThinkingDelta, ToolCallDelta, ToolCallComplete forwarding + ToolResult silent ignore |
| `agent-langchain4j/.../AgentSessionChatModel.java` | Streaming doChat: forward new event types to handler |
| `agent-langchain4j/.../AgentSessionChatModelTest.java` | Same |

### Not changed

- `InMemoryEndpointRegistry` — already correct
- `NoOpEndpointRegistry` — already silent
- `ClaudeAgentClient` / `ClaudeAgentSession` — still emit TextDelta only (upgrading to `messages()` is #119)
- `ChatModelAgentProvider` / `ChatModelAgentSession` — emits single TextDelta per invocation, no change
- `NoOpAgentProvider` / `NoOpAgentSession` — empty streams
- Blocking `doChat` methods — TextDelta filter still correct

### Deferred

- Upgrade `ClaudeAgentClient` to use `messages()` instead of `textStream()` to produce richer events (#119)
- Upgrade `ChatModelAgentProvider` / `ChatModelAgentSession` to use `StreamingChatModel` when available, mapping LangChain4j handler callbacks to the new event types (#120). Without this, LangChain4j-backed agents are limited to single-TextDelta responses. The prior spec (#105) planned for this ("If backing model is StreamingChatModel: stream via handler") but the v1 implementation used the blocking path only.
- UsageReport event type — no concrete consumer need yet

### Protocol compliance

| Protocol | Status |
|----------|--------|
| PP-20260615-9adaee (no-op must not fire CDI events) | Compliant — NoOp unchanged |
| PP-20260618-f2d154 (EndpointRegistered must not fire from no-op) | Compliant — NoOp unchanged |
| PP-20260522-platform-api-scope (platform-api is foundational only) | Compliant — AgentEvent is in agent-api, not platform-api |
| PP-20260601-81b9e5 (SPI evolution default methods) | N/A — adding types, not interface methods |
| PP-20260617-8de879 (unsupported format throws) | Compliant — format handling unchanged |
| PP-20260603-c36746 (agent CDI priority) | Compliant — no CDI priority changes |
