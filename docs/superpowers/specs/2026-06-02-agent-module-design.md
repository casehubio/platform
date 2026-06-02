# casehub-platform-agent — Design Spec

**Date:** 2026-06-02  
**Issue:** casehubio/platform#55  
**Status:** Approved for implementation

---

## Context

Every CaseHub application that dispatches AI agents faces the same infrastructure
problem: wiring a Claude Code CLI subprocess into a Quarkus service involves
subprocess management, Mutiny/Reactor bridging, `@Blocking` threading, MCP server
configuration, timeout handling, and lifecycle management. Without a shared module,
every app solves this independently.

This spec defines `casehub-platform-agent-api` and `casehub-platform-agent-claude`
to solve it once.

**Originating use case:** casehubio/drafthouse#29 — DebateAgentProvider, two
implementations: LangChain4j default + Claude Agent SDK alternative.

---

## Module Structure

Two flat modules at the platform repo root, following the existing `memory-jpa/`,
`memory-inmem/` naming convention. No aggregator POM.

```
agent-api/     casehub-platform-agent-api     — SPI types, Mutiny allowed
agent-claude/  casehub-platform-agent-claude  — Claude Agent SDK Quarkus integration
```

`agent-api/` is a deliberate exception to the `platform-api/` zero-dependency rule:
it depends on Mutiny because agent sessions are inherently streaming. `platform-api/`
itself is not affected. Consumers who don't use agents take neither module.

### Root POM changes

```xml
<module>agent-api</module>
<module>agent-claude</module>
```

### BOM entry (casehub-parent)

```xml
<dependency>
    <groupId>io.casehub.platform</groupId>
    <artifactId>casehub-platform-agent-api</artifactId>
    <version>${casehub-platform.version}</version>
</dependency>
<dependency>
    <groupId>io.casehub.platform</groupId>
    <artifactId>casehub-platform-agent-claude</artifactId>
    <version>${casehub-platform.version}</version>
</dependency>
```

---

## agent-api/ — SPI Types

**Package:** `io.casehub.platform.agent`  
**Dependencies:** Mutiny, slf4j only. No Quarkus, no claude-code-sdk, no casehubio imports.

### `AgentMcpServer`

Our own sealed interface in `agent-api/` — keeps `agent-api/` free of the SDK dep.
`ClaudeAgentClient` in `agent-claude/` converts these to `McpServerConfig` internally.

```java
public sealed interface AgentMcpServer
    permits AgentMcpServer.Stdio, AgentMcpServer.Sse, AgentMcpServer.Http {

    record Stdio(String command, List<String> args, Map<String, String> env)
        implements AgentMcpServer {
        public Stdio(String command) { this(command, List.of(), Map.of()); }
    }

    record Sse(String url, Map<String, String> headers) implements AgentMcpServer {
        public Sse(String url) { this(url, Map.of()); }
    }

    record Http(String url, Map<String, String> headers) implements AgentMcpServer {
        public Http(String url) { this(url, Map.of()); }
    }
}
```

### `AgentEvent`

```java
public sealed interface AgentEvent
    permits AgentEvent.TextOutput, AgentEvent.SessionComplete {

    record TextOutput(String text) implements AgentEvent {}

    // reason: "finish_tool" | "timeout" | "error"
    record SessionComplete(String reason) implements AgentEvent {}
}
```

Note: Claude Code tool calls happen inside the CLI subprocess and are not observable
as events. `ToolCall` is intentionally absent.

### `AgentSessionConfig`

```java
public record AgentSessionConfig(
    String systemPrompt,
    String userPrompt,                // null for connect() sessions
    List<AgentMcpServer> mcpServers,
    Optional<Duration> timeout        // empty = use ClaudeAgentProperties.defaultTimeout()
) {
    public AgentSessionConfig(String systemPrompt, String userPrompt) {
        this(systemPrompt, userPrompt, List.of(), Optional.empty());
    }

    public AgentSessionConfig(String systemPrompt, String userPrompt, Duration timeout) {
        this(systemPrompt, userPrompt, List.of(), Optional.of(timeout));
    }
}
```

MCP servers are external only (`AgentMcpServer.Stdio`, `Sse`, `Http`). `agent-claude/`
converts these to SDK `McpServerConfig` types at session start.
In-process Java tool handlers are out of scope for v1 — the primary agent use case
injects context into the prompt rather than exposing Java methods back to Claude mid-task.

### `AgentSession`

Multi-turn session handle returned by `ClaudeAgentClient.connect()`.

```java
public interface AgentSession extends AutoCloseable {

    /**
     * Send a follow-up prompt in this session and stream the response.
     * The session must be open (not cancelled or closed).
     */
    Multi<AgentEvent> query(String prompt);

    /**
     * Interrupt the session immediately. The subprocess is killed.
     * Use when you cannot wait for graceful shutdown.
     */
    void cancel();

    /**
     * Graceful shutdown. Waits for the current turn to complete, then
     * tears down the session. Safe for try-with-resources.
     */
    @Override
    void close();
}
```

### `AgentSessionLimitException`

```java
public class AgentSessionLimitException extends RuntimeException {
    public AgentSessionLimitException(int limit) {
        super("Agent session limit reached (" + limit + " concurrent sessions). " +
              "Set casehub.platform.agent.claude.max-concurrent-sessions to increase.");
    }
}
```

---

## agent-claude/ — Claude Agent SDK Quarkus Integration

**Dependencies:** `agent-api/`, `org.springaicommunity:claude-code-sdk:1.0.0`,
Quarkus CDI, Mutiny.

**Note on Maven coordinate:** The artifact listed in the originating issue
(`com.github.spring-ai-community:claude-agent-sdk-java:1.0.0`) is incorrect.
The correct coordinate confirmed on Maven Central is
`org.springaicommunity:claude-code-sdk:1.0.0`.

### `ClaudeAgentClient`

```java
@ApplicationScoped
public class ClaudeAgentClient {

    /**
     * Single-shot invocation. Streams events until the agent signals completion
     * or timeout. No session object to manage — subscribe and consume.
     *
     * Safe to call from any thread. The underlying SDK uses @Blocking internally.
     *
     * @throws AgentSessionLimitException if maxConcurrentSessions is reached
     */
    public Multi<AgentEvent> run(AgentSessionConfig config) { ... }

    /**
     * Multi-turn session. Returns an AgentSession for subsequent query() calls.
     * The caller is responsible for closing the session.
     *
     * @throws AgentSessionLimitException if maxConcurrentSessions is reached
     */
    public AgentSession connect(AgentSessionConfig config) { ... }
}
```

**Concurrency cap:** enforced by a `Semaphore(maxConcurrentSessions)`. Both `run()`
and `connect()` attempt to acquire before proceeding. If the semaphore cannot be
acquired immediately (non-blocking try), `AgentSessionLimitException` is thrown.
The semaphore is released when the `Multi` completes, fails, or is cancelled (run),
or when the session is closed or cancelled (connect). Release happens in a
`Uni.onTermination()` / finally block to guarantee it on all exit paths.

**Reactor → Mutiny bridging:** The SDK returns `Flux<Message>`. Bridge via
`Multi.createFrom().publisher(flux)`, then map SDK message types to `AgentEvent`.

**Timeout:** applied as `.ifNoItem().after(config.timeout()).fail()` on the Mutiny
stream. On timeout: the session is cancelled and `SessionComplete("timeout")` is
emitted before the stream terminates. If timeout is null, `ClaudeAgentProperties
.defaultTimeout()` is used.

**`@PreDestroy`:** closes all active sessions (cancel semantics) on application
shutdown.

### `ClaudeAgentProperties`

```java
@ConfigMapping(prefix = "casehub.platform.agent.claude")
public interface ClaudeAgentProperties {

    /** Path to claude CLI binary. Default: resolved from PATH. */
    Optional<String> binaryPath();

    /** Default timeout when AgentSessionConfig.timeout() is null. Default: 5 minutes. */
    @WithDefault("PT5M")
    Duration defaultTimeout();

    /** Maximum concurrent sessions. Excess calls throw AgentSessionLimitException. */
    @WithDefault("4")
    int maxConcurrentSessions();
}
```

---

## CDI Priority Pattern

The standard pattern for application SPIs with both a LangChain4j and a Claude
Agent SDK implementation:

```java
// LangChain4j — always present, works with any LLM, @DefaultBean
@DefaultBean
@ApplicationScoped
public class LangChain4jDebateAgentProvider implements DebateAgentProvider {
    @RegisterAiService interface DebateAiService { ... }
    @Inject DebateAiService aiService;
}

// Claude Agent SDK — activates by classpath presence of agent-claude
@Alternative @Priority(1)
@ApplicationScoped
public class ClaudeDebateAgentProvider implements DebateAgentProvider {
    @Inject ClaudeAgentClient client;
}
```

No registration call needed. Follows `persistence-backend-cdi-priority.md`.

---

## Testing Pattern

### Integration test gating

ITs that invoke the real Claude CLI are gated with JUnit 5's environment variable
condition — no build-time processing required, compatible with library modules that
omit `quarkus:build`:

```java
@EnabledIfEnvironmentVariable(named = "CLAUDE_AGENT_TESTS_ENABLED", matches = "true")
class ClaudeAgentClientIT {
    @Inject ClaudeAgentClient client;
    // ...
}
```

Set `CLAUDE_AGENT_TESTS_ENABLED=true` locally when Claude CLI is installed and
authenticated. CI runs without it.

### Parity contract tests

Application SPIs with two implementations use a shared abstract contract test.
Both implementations pass the same structural cases.

```java
public abstract class DebateAgentProviderContractTest {
    protected abstract DebateAgentProvider provider();

    @Test void outputHasRequiredFields() { ... }
    @Test void outputTypeIsValidEnum() { ... }
}

// LangChain4j — always runs in CI
class LangChain4jDebateAgentProviderTest extends DebateAgentProviderContractTest {
    protected DebateAgentProvider provider() {
        return new LangChain4jDebateAgentProvider(mockChatModel());
    }
}

// Claude — opt-in locally
@EnabledIfEnvironmentVariable(named = "CLAUDE_AGENT_TESTS_ENABLED", matches = "true")
class ClaudeDebateAgentProviderTest extends DebateAgentProviderContractTest {
    @Inject ClaudeAgentClient client;
    protected DebateAgentProvider provider() {
        return new ClaudeDebateAgentProvider(client);
    }
}
```

---

## Out of Scope for v1

- **In-process MCP tools (`McpToolDefinition`, `McpSdkServerConfig`):** The primary
  casehub agent use case injects context into the prompt rather than exposing Java
  methods back to Claude mid-task. Revisit if an open-ended investigation use case
  emerges that requires Claude to query application data on demand.
- **Multi-model support:** `ClaudeAgentProperties` targets Claude CLI only. Other
  models are served by the LangChain4j `@DefaultBean` path.

---

## Protocol to Capture (post-ship)

Capture `ai-agent-provider-cdi-priority.md` in `casehub/garden/docs/protocols/universal/`:

> When an application SPI has both a LangChain4j implementation (any LLM, portable)
> and a Claude Agent SDK implementation (full tool access, Claude only), use
> `@DefaultBean` for LangChain4j and `@Alternative @Priority(1)` for Claude Agent SDK.
> The Claude implementation activates by classpath presence of
> `casehub-platform-agent-claude`. Parity contract tests must cover both.

---

## Downstream Migration (post-ship)

After this module ships, open a tracking issue in each repo to review their AI agent
/ LLM integration and adopt the CDI priority pattern where the Claude SDK alternative
adds value. Priority order:

1. `casehubio/drafthouse` — originating use case, `DebateAgentProvider`
2. `casehubio/eidos` — `SystemPromptRenderer` with optional `ChatModel`
3. `casehubio/engine` — `casehub-engine-ai` embedding and routing strategies
4. `casehubio/devtown`, `casehubio/aml`, `casehubio/clinical` — confirm LLM usage
   before opening migration issues

`casehubio/openclaw`: the `WorkerProvisioner` SPI wraps Claude Code via tmux —
assess whether `ClaudeAgentClient` is additive or redundant before opening.

---

## References

- casehubio/platform#55 — originating issue (design discussion + dep analysis)
- casehubio/drafthouse#29 — originating use case
- `persistence-backend-cdi-priority.md` — CDI priority pattern this follows
- `spi-adapter-module-placement.md` — module starts in platform; extract if cross-org
- Maven Central: `org.springaicommunity:claude-code-sdk:1.0.0`
