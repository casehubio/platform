# casehub-platform-agent-api

AgentProvider SPI and API types for AI agent invocation.

## Why AgentProvider Exists

AgentProvider is the platform's agent SPI — the contract consumers inject to invoke AI agents.

Each vendor has a native `AgentBackend` implementation that talks directly to the vendor's SDK or CLI. This avoids intermediary abstraction layers and preserves vendor-specific advantages:

- **Claude CLI prompt caching** — the CLI manages `cache_control` breakpoints transparently at the Anthropic API level.
- **MCP server wiring** — Claude CLI accepts MCP server configurations (stdio, SSE, HTTP) per invocation.
- **Native OpenAI SDK** — direct access to OpenAI's Java SDK with prompt cache key support.
- **Subprocess lifecycle** — CLI-based providers run as managed subprocesses with drain-on-close, semaphore-based concurrency limits, and wall-clock timeout enforcement.

## CDI Tier Structure

**AgentProvider tiers:**

| Tier | Bean | Annotation | Activates when |
|------|------|------------|----------------|
| 0 | `NoOpAgentProvider` | `@DefaultBean` | Nothing else on classpath |
| 10 | `ClaudeAgentProvider` | `@Alternative @Priority(10)` | Claude on classpath |

**Available backends:** claude, openai, ollama, gemini, gemini-cli, codex

`RoutingAgentProvider` (in `agent-router/`) selects the appropriate backend via model resolution. `GatedAgentProvider` (in `agent-gate/`) decorates with rate limiting.

## API Types

- `AgentProvider` — `invoke()` (single-shot streaming) + `openSession()` (multi-turn) + `invokeBlocking()` (blocking convenience returning `AgentInvocationResult`)
- `AgentBackend` — implementor SPI: `key()` + `invoke()` + `openSession()`
- `AgentSession` — serial multi-turn: `query()`, `interrupt()`, `close()`
- `AgentSessionConfig` — single-shot config: systemPrompt, userPrompt, mcpServers, timeout, correlationId, model, modelQuery, responseFormat
- `AgentSessionInit` — session-open config: systemPrompt, mcpServers, timeout, correlationId, model, modelQuery, responseFormat
- `AgentEvent` — sealed: `TextDelta`, `ThinkingDelta`, `ToolCallDelta`, `ToolCallComplete`, `ToolResult`, `InvocationComplete`
- `AgentInvocationResult` — blocking result record: text, token counts, cost, timing metadata
- `ResponseFormat` — structured output: `TEXT` (default), `json(name, schema)` (typed JSON), `jsonAny()` (schemaless JSON)
- `AgentMcpServer` — sealed: `Stdio`, `Sse`, `Http`
- Exceptions: `AgentTimeoutException`, `AgentProcessException`, `AgentSessionLimitException`, `AgentRateLimitException`
