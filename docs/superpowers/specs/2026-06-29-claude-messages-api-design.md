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
| — | `ToolCallDelta` | Not emitted — Claude SDK delivers complete tool calls, not streaming fragments (#118) |
| — | `ToolResult` | Not emitted — CLI manages tool execution internally; tool results not surfaced to SDK consumer |
| `ResultMessage` | — | Cost/usage metadata; deferred (#123) |
| Other `ContentBlock` subtypes | — | Silently skipped via `default` branch (debug-logged) |
| Non-`AssistantMessage` types | — | Silently skipped (guard clause) |

**ToolResult rationale:** `ToolResultBlock` appears in `UserMessage` content (per the
Anthropic API model), not in `AssistantMessage.content()`. The mapper filters to
`AssistantMessage` only — `ToolResultBlock` would be unreachable even if mapped. More
fundamentally, the Claude Code CLI manages tool execution as an internal conversation
loop; tool results are not surfaced to the SDK consumer's `messages()` stream. A future
backend (e.g., raw Anthropic API with external tool dispatch) that exposes tool results
would add this mapping.

**ToolCallDelta rationale:** documented in #118 — "Backends that deliver complete tool
calls (e.g. Claude SDK via `messages()`) emit only `ToolCallComplete`, never
`ToolCallDelta`."

### Tool call indexing

An `AtomicInteger toolIndex` is passed to the mapper from the caller. Indices are
sequential across all messages within a single invocation (single-shot) or turn
(multi-turn session). The counter resets per turn in multi-turn sessions.

### JSON serialization

`ToolUseBlock.input()` returns `Map<String, Object>` (deserialized by Jackson in the
SDK). `ToolCallComplete.arguments` expects a JSON `String`. A module-local static
`ObjectMapper` (default config) round-trips the map back to JSON. No CDI injection
needed — default config is correct by definition for values Jackson itself produced.
A static `Logger` provides diagnostic visibility: WARN on serialization failure (should
never fire, but makes the failure mode observable), DEBUG on unrecognized content block
types (forward-compatibility with future SDK `ContentBlock` subtypes).

### Non-sealed ContentBlock

`ContentBlock` is a non-sealed interface (Jackson `@JsonSubTypes` polymorphism, not
Java `sealed`). Per JEP 441 (Java 21), the pattern-matching switch requires a `default`
branch for exhaustiveness. The `default` branch debug-logs the unrecognized type and
skips it — forward-compatible with future SDK content block types.

## Changes

### MessageEventMapper.java (new, package-private)

```java
class MessageEventMapper {
    private static final Logger LOG = Logger.getLogger(MessageEventMapper.class);
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
                default -> LOG.debugf("Skipping unrecognized ContentBlock type: %s",
                    block.getType());
            }
        }
        return events;
    }

    private static String serializeInput(Map<String, Object> input) {
        if (input == null || input.isEmpty()) return "{}";
        try {
            return MAPPER.writeValueAsString(input);
        } catch (Exception e) {
            LOG.warnf("Failed to serialize tool input, defaulting to {}: %s",
                e.getMessage());
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

### Javadoc updates

| Method | Current | Updated |
|--------|---------|---------|
| `ClaudeAgentClient.run()` | "Stream {@link AgentEvent.TextDelta} events until the agent completes or the wall-clock timeout fires." | "Stream {@link AgentEvent} events (text, thinking, tool calls) until the agent completes or the wall-clock timeout fires." |
| `ClaudeAgentClient.buildEventStream()` | "bridges {@code Flux<String>} to {@code Multi<AgentEvent>}" | "bridges {@code Flux<Message>} to {@code Multi<AgentEvent>} via {@link MessageEventMapper}" |

### ClaudeAgentClientIT update

Update `invoke_returnsAtLeastOneTextDelta` assertion. The current
`allSatisfy(isInstanceOf(TextDelta))` will fail when the CLI emits `ThinkingDelta` or
`ToolCallComplete` events (which it may, depending on Claude's response). Change to:

```java
assertThat(events).isNotEmpty();
assertThat(events).anyMatch(e -> e instanceof AgentEvent.TextDelta);

String fullText = events.stream()
        .filter(AgentEvent.TextDelta.class::isInstance)
        .map(e -> ((AgentEvent.TextDelta) e).text())
        .collect(Collectors.joining());
assertThat(fullText.toLowerCase()).contains("hello");
```

The text content verification is unchanged — it filters for `TextDelta` events only.

### MessageEventMapperTest.java (new)

Pure JUnit 5 unit tests — no CDI, no Quarkus. Tests `MessageEventMapper.toEvents()` directly.

| Scenario | Input | Expected |
|----------|-------|----------|
| TextBlock → TextDelta | `AssistantMessage([TextBlock("hello")])` | `[TextDelta("hello")]` |
| Empty TextBlock skipped | `AssistantMessage([TextBlock("")])` | `[]` |
| ThinkingBlock → ThinkingDelta | `AssistantMessage([ThinkingBlock("reasoning", null)])` | `[ThinkingDelta("reasoning")]` |
| ToolUseBlock → ToolCallComplete | `AssistantMessage([ToolUseBlock("tu1", "Read", {path: "f.java"})])` | `[ToolCallComplete(0, "tu1", "Read", "{\"path\":\"f.java\"}")]` |
| Mixed blocks in one message | `AssistantMessage([ThinkingBlock, TextBlock, ToolUseBlock])` | `[ThinkingDelta, TextDelta, ToolCallComplete]` — order preserved |
| Tool index increments across messages | Two messages each with ToolUseBlock | indices 0, 1 (shared counter) |
| Unknown ContentBlock type skipped | `AssistantMessage([custom ContentBlock impl])` | `[]` |
| ResultMessage skipped | `ResultMessage(...)` | `[]` |
| UserMessage skipped | `UserMessage("hello")` | `[]` |
| SystemMessage skipped | `SystemMessage("init", Map.of())` | `[]` |

### No changes to

- `AgentEvent` sealed interface (agent-api) — all variants exist from #118
- `AgentProvider` SPI — unchanged
- `ClaudeAgentProperties` — no new config
- `ClaudeAgentProvider` — delegates to `ClaudeAgentClient`, unaffected
- Existing `ClaudeAgentClientTest` — tests semaphore/timeout/lifecycle via streamFactory, not event mapping
- Existing `ClaudeAgentSessionTest` — tests state machine/close/interrupt, not event mapping

## Backward compatibility

All downstream consumers of `AgentEvent` filter for specific event types via `instanceof`
or `.filter()`. New event types in the stream are silently ignored:

| Consumer | Filter pattern | Safe? |
|----------|---------------|-------|
| `PipelineProvisioner.provisionAiReview()` | `.filter(AgentEvent.TextDelta.class::isInstance)` | Yes |
| `OpenClawChatModel.doChat()` | `.filter(AgentEvent.TextDelta.class::isInstance)` | Yes |
| `AgentProviderChatModel.doChat()` (blocking) | `.filter(e -> e instanceof AgentEvent.TextDelta)` | Yes |
| `AgentSessionChatModel.doChat()` (blocking) | `.filter(e -> e instanceof AgentEvent.TextDelta)` | Yes |
| `AgentSessionChatModel.doChat(req, handler)` (streaming) | `instanceof` cascade for all event types | Yes — already forwards ThinkingDelta, ToolCallDelta, ToolCallComplete (#118) |
