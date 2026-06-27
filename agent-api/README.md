# casehub-platform-agent-api

AgentProvider SPI and API types for AI agent invocation.

## Why AgentProvider Exists

AgentProvider is the platform's agent SPI — the contract consumers inject to invoke AI agents.

LangChain4j's `ChatModel`/`StreamingChatModel` is the de facto standard Java LLM interface. AgentProvider does not replace it. AgentProvider exists because some providers have **native SDK advantages that LangChain4j can't express**:

- **Claude CLI prompt caching** — the CLI manages `cache_control` breakpoints transparently at the Anthropic API level. LangChain4j's `SystemMessage` has no `cache_control` field, and LangChain4j mediates every call, so even a native SDK behind `doChat()` loses caching control.
- **MCP server wiring** — Claude CLI accepts MCP server configurations (stdio, SSE, HTTP) per invocation. No LangChain4j equivalent.
- **Subprocess lifecycle** — Claude CLI runs as a managed subprocess with drain-on-close, semaphore-based concurrency limits, and wall-clock timeout enforcement.

Platform infrastructure (Mutiny reactive streaming, typed error taxonomy, session lifecycle management) could be layered on top of ChatModel. AgentProvider packages these coherently but they are not capabilities LangChain4j lacks.

## Two Implementation Strategies

### 1. Native SDK (bypass LangChain4j)

For providers where the native SDK offers advantages. `ClaudeAgentProvider` is the current example — it shells out to the Claude CLI subprocess, preserving prompt caching and MCP support.

### 2. LangChain4j-backed (default)

For everything else. `ChatModelAgentProvider` (in `agent-langchain4j/`) wraps any LangChain4j `ChatModel` or `StreamingChatModel` as an AgentProvider. OpenAI, Gemini, Ollama, Mistral — any model with a LangChain4j implementation works with zero provider-specific code.

## CDI Tier Structure

**AgentProvider tiers:**

| Tier | Bean | Annotation | Activates when |
|------|------|------------|----------------|
| 0 | `NoOpAgentProvider` | `@DefaultBean` | Nothing else on classpath |
| 1 | `ChatModelAgentProvider` | `@Alternative @Priority(1)` | LangChain4j ChatModel available, no native provider |
| 10 | `ClaudeAgentProvider` | `@Alternative @Priority(10)` | Claude on classpath (always wins) |

**ChatModel tiers (separate — `@DefaultBean` system):**

| Bean | Annotation | When selected |
|------|------------|---------------|
| quarkus-langchain4j ChatModel | `@DefaultBean` (implicit `@Priority(0)`) | Only non-platform ChatModel on classpath |
| `AgentProviderChatModel` | `@DefaultBean @Priority(10)` | Beats raw ChatModel when both exist; suppressed by consumer-provided non-`@DefaultBean` |

## Interop with Engine

Engine code uses `ChatModel` directly (for JSON mode, tool use, parameters). The generic `AgentProviderChatModel` adapter (in `agent-langchain4j/`) wraps any AgentProvider as a ChatModel, supporting JSON format via prompt engineering.

## AgentProviderChatModel — Restricted Adapter

`AgentProviderChatModel` intentionally does not implement the full `ChatModel` contract. AgentProvider owns session history — passing `AiMessage` from outside is a caller error.

**Supported:** Single-shot calls with `SystemMessage` + `UserMessage`.
**Unsupported:** AI Services with external `ChatMemory`, any client managing message history with `AiMessage`.

The adapter throws `IllegalArgumentException` on `AiMessage` with a message explaining why.

## API Types

- `AgentProvider` — `invoke()` (single-shot streaming) + `openSession()` (multi-turn)
- `AgentSession` — serial multi-turn: `query()`, `interrupt()`, `close()`
- `AgentSessionConfig` — single-shot config: systemPrompt, userPrompt, mcpServers, timeout, correlationId
- `AgentSessionInit` — session-open config: systemPrompt, mcpServers, timeout, correlationId
- `AgentEvent` — sealed: `TextDelta`, `ThinkingDelta`, `ToolCallDelta`, `ToolCallComplete`, `ToolResult`
- `AgentMcpServer` — sealed: `Stdio`, `Sse`, `Http`
- Exceptions: `AgentTimeoutException`, `AgentProcessException`, `AgentSessionLimitException`
