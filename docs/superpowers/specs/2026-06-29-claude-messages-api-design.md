# ClaudeAgentClient — messages() API Upgrade

**Issue:** casehubio/platform#119
**Date:** 2026-06-29
**Status:** Approved

## Problem

`ClaudeAgentClient.buildEventStream()` and `ClaudeAgentSession.buildTurnStream()` use
the Claude SDK's `textStream()` API, which returns `Flux<String>` — token-level text
deltas only. After #118 extended `AgentEvent` with `ThinkingDelta`, `ToolCallDelta`,
`ToolCallComplete`, and `ToolResult`, the Claude backend emits none of them.

The SDK's `messages()` API returns `Flux<Message>` with access to `AssistantMessage`
(containing `TextBlock`, `ThinkingBlock`, `ToolUseBlock` content blocks) and
`ToolResultBlock`. This richer data maps directly to the new `AgentEvent` types.

## Design

### Approach: Switch to messages(), extract shared mapper

Replace `textStream()` with `messages()` in both stream-building methods. Extract
the `Message → List<AgentEvent>` mapping to a shared package-private utility class
(`MessageEventMapper`) used by both `ClaudeAgentClient` and `ClaudeAgentSession`.

### Content block mapping

| SDK type | AgentEvent | Notes |
|----------|-----------|-------|
| `TextBlock.text()` | `TextDelta(text)` | Skip if empty |
| `ThinkingBlock.thinking()` | `ThinkingDelta(thinking)` | Skip if null/empty |
| `ToolUseBlock(id, name, input)` | `ToolCallComplete(index, id, name, jsonArgs)` | `input` Map serialized via static ObjectMapper |
| `ToolResultBlock(toolUseId, content, isError)` | `ToolResult(toolUseId, contentStr, isError)` | String content direct; non-String via toString(); null → "" |
| `ResultMessage` | — | Cost/usage metadata; no AgentEvent counterpart |
| Other `Message` types | — | Silently skipped |

### Tool call indexing

An `AtomicInteger toolIndex` is passed to the mapper from the caller. Indices are
sequential across all messages within a single invocation (single-shot) or turn
(multi-turn session). The counter resets per turn in multi-turn sessions.

### JSON serialization

`ToolUseBlock.input()` returns `Map<String, Object>` (deserialized by Jackson in the
SDK). `ToolCallComplete.arguments` expects a JSON `String`. A module-local static
`ObjectMapper` (default config) round-trips the map back to JSON. No CDI injection
needed — default config is correct by definition for values Jackson itself produced.

## Changes

### MessageEventMapper.java (new, package-private)

```java
class MessageEventMapper {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static List<AgentEvent> toEvents(Message message, AtomicInteger toolIndex) {
        if (!(message instanceof AssistantMessage am)) return List.of();
        List<AgentEvent> events = new ArrayList<>();
        for (ContentBlock block : am.content()) {
            switch (block) {
                case TextBlock tb -> {
                    if (!tb.text().isEmpty())
                        events.add(new AgentEvent.TextDelta(tb.text()));
                }
                case ThinkingBlock tb -> {
                    if (tb.thinking() != null && !tb.thinking().isEmpty())
                        events.add(new AgentEvent.ThinkingDelta(tb.thinking()));
                }
                case ToolUseBlock tu -> {
                    String args = serializeInput(tu.input());
                    events.add(new AgentEvent.ToolCallComplete(
                        toolIndex.getAndIncrement(), tu.id(), tu.name(), args));
                }
                case ToolResultBlock tr -> {
                    String content = tr.getContentAsString();
                    if (content == null)
                        content = tr.content() != null ? tr.content().toString() : "";
                    events.add(new AgentEvent.ToolResult(
                        tr.toolUseId(), content, Boolean.TRUE.equals(tr.isError())));
                }
            }
        }
        return events;
    }

    private static String serializeInput(Map<String, Object> input) {
        if (input == null || input.isEmpty()) return "{}";
        try {
            return MAPPER.writeValueAsString(input);
        } catch (Exception e) {
            return "{}";
        }
    }
}
```

### ClaudeAgentClient.buildEventStream() — lines 226-231

Replace:
```java
Flux<String> textFlux = sdkClient.connect(config.userPrompt()).textStream();
Multi<AgentEvent> eventStream = Multi.createFrom()
    .publisher(JdkFlowAdapter.publisherToFlowPublisher(textFlux))
    .map(text -> (AgentEvent) new AgentEvent.TextDelta(text))
```

With:
```java
Flux<Message> messageFlux = sdkClient.connect(config.userPrompt()).messages();
AtomicInteger toolIndex = new AtomicInteger(0);
Multi<AgentEvent> eventStream = Multi.createFrom()
    .publisher(JdkFlowAdapter.publisherToFlowPublisher(messageFlux))
    .onItem().transformToMultiAndConcatenate(
        msg -> Multi.createFrom().iterable(MessageEventMapper.toEvents(msg, toolIndex)))
```

All downstream handlers (timeout, cleanup, error transform, termination) are unchanged.

### ClaudeAgentSession.buildTurnStream() — lines 242-248

Replace:
```java
final var textFlux = sessionStarted.compareAndSet(false, true)
    ? sdkClient.connect(prompt).textStream()
    : sdkClient.query(prompt).textStream();
return Multi.createFrom()
    .publisher(JdkFlowAdapter.publisherToFlowPublisher(textFlux))
    .map(text -> (AgentEvent) new AgentEvent.TextDelta(text));
```

With:
```java
final Flux<Message> messageFlux = sessionStarted.compareAndSet(false, true)
    ? sdkClient.connect(prompt).messages()
    : sdkClient.query(prompt).messages();
final AtomicInteger toolIndex = new AtomicInteger(0);
return Multi.createFrom()
    .publisher(JdkFlowAdapter.publisherToFlowPublisher(messageFlux))
    .onItem().transformToMultiAndConcatenate(
        msg -> Multi.createFrom().iterable(MessageEventMapper.toEvents(msg, toolIndex)));
```

`toolIndex` is per-turn — indices reset between turns in multi-turn sessions.

### MessageEventMapperTest.java (new)

Pure JUnit 5 unit tests — no CDI, no Quarkus. Tests `MessageEventMapper.toEvents()` directly.

| Scenario | Input | Expected |
|----------|-------|----------|
| TextBlock → TextDelta | `AssistantMessage([TextBlock("hello")])` | `[TextDelta("hello")]` |
| Empty TextBlock skipped | `AssistantMessage([TextBlock("")])` | `[]` |
| ThinkingBlock → ThinkingDelta | `AssistantMessage([ThinkingBlock("reasoning", null)])` | `[ThinkingDelta("reasoning")]` |
| ToolUseBlock → ToolCallComplete | `AssistantMessage([ToolUseBlock("tu1", "Read", {path: "f.java"})])` | `[ToolCallComplete(0, "tu1", "Read", "{\"path\":\"f.java\"}")]` |
| ToolResultBlock → ToolResult | `AssistantMessage([ToolResultBlock("tu1", "file content", false)])` | `[ToolResult("tu1", "file content", false)]` |
| ToolResultBlock error flag | `AssistantMessage([ToolResultBlock("tu1", "not found", true)])` | `[ToolResult("tu1", "not found", true)]` |
| ToolResultBlock null content | `AssistantMessage([ToolResultBlock("tu1", null, false)])` | `[ToolResult("tu1", "", false)]` |
| Mixed blocks in one message | `AssistantMessage([ThinkingBlock, TextBlock, ToolUseBlock])` | `[ThinkingDelta, TextDelta, ToolCallComplete]` — order preserved |
| Tool index increments across messages | Two messages each with ToolUseBlock | indices 0, 1 (shared counter) |
| ResultMessage skipped | `ResultMessage(...)` | `[]` |

### No changes to

- `AgentEvent` sealed interface (agent-api) — all variants exist from #118
- `AgentProvider` SPI — unchanged
- `ClaudeAgentProperties` — no new config
- `ClaudeAgentProvider` — delegates to `ClaudeAgentClient`, unaffected
- Existing `ClaudeAgentClientTest` — tests semaphore/timeout/lifecycle via streamFactory, not event mapping
- Existing `ClaudeAgentSessionTest` — tests state machine/close/interrupt, not event mapping
