# ReAct Tool-Use Loop — Design Spec

**Issue:** casehubio/platform#643
**Epic:** casehubio/platform#635 (all agent/LLM abstractions in platform)
**Date:** 2026-10-19

## Summary

Extract engine's ReAct tool-use loop into a new platform module `agent-react-core`.
Framework-neutral (no CDI, no Spring). Depends on `langchain4j-core` only. Both
Quarkus and Spring paths use the same loop directly — no framework-specific variants,
no Spring AI adapter.

## Module

**Artifact:** `casehub-platform-agent-react-core`
**Package:** `io.casehub.platform.agent.react`
**Dependencies:** `dev.langchain4j:langchain4j-core` (already in dependency tree)
**No CDI wiring module.** Consumers instantiate directly or produce in their own beans.

## Types

### Tool (record)

Binds a tool definition to its execution. LangChain4j's `ToolSpecification` is the
tool definition type (per D1 — no platform-owned parallel to avoid split brain).
Platform's contribution is the binding and execution contract.

```java
public record Tool(ToolSpecification spec, ToolExecutor executor) {}
```

### ToolExecutor (functional interface)

Executes a tool call. Returns the output as a string (JSON or plain text).
Exceptions are caught by the loop and fed back to the LLM as error results (D4).

```java
@FunctionalInterface
public interface ToolExecutor {
    String execute(String name, String argumentsJson);
}
```

### ToolUseConfig (record)

Configuration for a single loop invocation.

```java
public record ToolUseConfig(
    ChatModel model,
    String systemPrompt,
    List<Tool> tools,
    int maxCycles,
    ToolUseListener listener
) {
    // Convenience: default listener (no-op), default maxCycles (10)
}
```

### ToolUseLoop (class)

The core loop. Blocking — caller owns the thread (D2). Stateless — one instance
serves all invocations.

```java
public class ToolUseLoop {
    public ToolUseResult run(ToolUseConfig config, String userMessage) { ... }
}
```

**Flow:**

1. Build `Map<String, Tool>` from `config.tools()`, keyed by `spec.name()`
2. Build `List<ToolSpecification>` extracted from each `Tool.spec()`
3. Init messages: `[SystemMessage(systemPrompt), UserMessage(userMessage)]`
4. **Cycle loop** (while `cycleCount < maxCycles` and `!Thread.interrupted()`):
   - Build `ChatRequest` with messages + toolSpecs
   - Call `config.model().chat(request)` → `ChatResponse`
   - Append `AiMessage` to messages, accumulate token counts
   - If `!aiMessage.hasToolExecutionRequests()` → return `ToolUseResult` with `COMPLETED`
   - For each `ToolExecutionRequest`:
     - Resolve tool by name from lookup map
     - Not found → error result: `"Error: unknown tool '<name>'"`
     - Found → call `tool.executor().execute(name, arguments)`, catch exceptions → error result with exception message
     - Record `ToolCallRecord` with timing
   - Append `ToolExecutionResultMessage.from(request, output)` for each tool call
   - Fire `config.listener().onCycle(cycleEvent)`
5. Loop exhausted → return with `MAX_CYCLES_REACHED` (last reasoning text preserved)
6. Thread interrupted → return with `INTERRUPTED`

**Listener exception policy:** Swallowed and logged. Observability must not abort the loop.

### ToolUseResult (record)

Structured result with full audit trail.

```java
public record ToolUseResult(
    String text,
    long inputTokens,
    long outputTokens,
    int cycleCount,
    List<ToolCallRecord> toolCalls,
    Duration elapsed,
    TerminationReason reason
) {}
```

### ToolCallRecord (record)

Per-call audit entry.

```java
public record ToolCallRecord(
    String name,
    String arguments,
    String output,
    boolean error,
    Duration duration
) {}
```

### ToolUseCycleEvent (record)

Per-cycle observability event, passed to `ToolUseListener` (D3).

```java
public record ToolUseCycleEvent(
    int cycleIndex,
    String reasoningText,
    List<ToolCallRecord> toolCalls,
    long inputTokens,
    long outputTokens
) {}
```

### ToolUseListener (functional interface)

Per-cycle callback. Framework-neutral — consumers wire to CDI events, Spring
ApplicationEventPublisher, logging, metrics, or anything else (D3).

```java
@FunctionalInterface
public interface ToolUseListener {
    void onCycle(ToolUseCycleEvent event);

    static ToolUseListener noOp() { return event -> {}; }
}
```

### TerminationReason (enum)

```java
public enum TerminationReason {
    COMPLETED,          // LLM responded with text (no tool calls)
    MAX_CYCLES_REACHED, // Loop exhausted maxCycles
    INTERRUPTED         // Thread.interrupted() detected
}
```

## What engine adapts

Engine's existing types map to platform's:

| Engine | Platform |
|--------|----------|
| `ToolSource.LocalTool(name, desc, fn, schema)` | `new Tool(toolSpec, (name, args) -> fn.apply(args))` |
| `ToolSource.WorkerTool(capability, workerName)` | `new Tool(toolSpec, (name, args) -> workerRuntime.execute(name, args))` |
| `ReActWorkerFunction` | `new ToolUseConfig(chatModel, systemPrompt, tools, maxCycles, listener)` |
| `ReActCycleEventHandler` | `ToolUseListener` impl that writes to EventLogRepository |
| `ToolCallRecord` (engine) | `ToolCallRecord` (platform) — same shape |

Engine removes its `io.casehub.engine.react` package and depends on
`casehub-platform-agent-react-core` instead.

## What this does NOT include

- **No CDI wiring module** — the loop is a POJO, consumers produce it themselves
- **No Spring AI adapter** — both frameworks use ToolUseLoop directly (D5)
- **No AiServices dependency** — uses langchain4j-core only (D5)
- **No platform-owned ToolDefinition** — ToolSpecification is the lingua franca (D1)
- **No built-in threading** — caller owns the thread (D2)

## Testing

- Mock `ChatModel` with sequential responses (tool calls → text)
- Verify: loop executes tools and returns final text
- Verify: unknown tool name → error fed back → LLM self-corrects
- Verify: maxCycles exceeded → MAX_CYCLES_REACHED with last reasoning
- Verify: executor exception → error-as-result, loop continues
- Verify: Thread.interrupted() → INTERRUPTED
- Verify: token counts accumulate across cycles
- Verify: ToolUseListener receives per-cycle events
- Verify: listener exception does not abort the loop

## References

- engine-support-core `io.casehub.engine.react` package — source implementation
- platform agent-router-core `RoutingChatModel` — ChatModel routing layer
- LangChain4j 1.19.0 `langchain4j-core` — ChatModel, ToolSpecification, message types
- Epic casehubio/platform#635 — "all agent stuff needs to be in platform"
- casehubio/platform#640 — LangChain4j reconciliation (prerequisite, landed)
