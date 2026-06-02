# casehub-platform-agent — Design Spec

**Date:** 2026-06-02
**Issue:** casehubio/platform#55
**Status:** Approved for implementation

---

## Context

Every CaseHub application that dispatches AI agents faces the same infrastructure
problem: wiring a Claude Code CLI subprocess into a Quarkus service involves
subprocess management, Mutiny/Reactor bridging, worker-thread safety, MCP server
configuration, timeout handling, and lifecycle management. Without a shared module,
every app solves this independently.

This spec defines two flat modules:

- `casehub-platform-agent-api` — SPI types and `AgentProvider` interface
- `casehub-platform-agent-claude` — Claude Agent SDK Quarkus integration

**Originating use case:** casehubio/drafthouse#29 — `DebateAgentProvider`, two
implementations: LangChain4j default + Claude Agent SDK alternative.

**Architectural decisions made:**
- Single-shot only (v1). `AgentSession` / multi-turn deferred to v2 — no current use
  case requires it, and subprocess-held conversational state has no recovery path on
  crash. State persistence uses `CaseMemoryStore` instead.
- `AgentProvider` SPI in `agent-api/` with `NoOpAgentProvider @DefaultBean` in
  `platform/`. Apps inject the SPI, not `ClaudeAgentClient` directly.

---

## Module Structure

Flat modules at repo root, following `memory-jpa/`, `memory-inmem/` convention.
No aggregator POM.

```
agent-api/     casehub-platform-agent-api     — AgentProvider SPI + types (Mutiny allowed)
agent-claude/  casehub-platform-agent-claude  — Claude Agent SDK Quarkus integration
```

`agent-api/` depends on Mutiny — a deliberate exception to the `platform-api/`
zero-dependency rule. Agent sessions are inherently streaming; the exception is bounded
to this module. `platform-api/` is unaffected. The `NoOpAgentProvider @DefaultBean`
lives in the existing `platform/` module.

### Root POM

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

**Reactor transitive dependency:** `org.springaicommunity:claude-code-sdk:1.0.0`
transitively pulls `reactor-core 3.7.0` (via `io.modelcontextprotocol.sdk:mcp:0.15.0`).
Quarkus 3.32.2 does not manage `reactor-core` in its BOM — no conflict. Both Mutiny
and Reactor will be on the classpath of any app that depends on `agent-claude/`.
This is expected and documented.

---

## agent-api/ — SPI Types

**Package:** `io.casehub.platform.agent`
**Dependencies:** Mutiny, slf4j only. No Quarkus, no claude-code-sdk, no casehubio imports.

### `AgentProvider`

The SPI. Apps inject this — they never depend on `ClaudeAgentClient` directly.

```java
public interface AgentProvider {
    /**
     * Invoke the agent with the given config and stream response events.
     *
     * The returned Multi completes when the agent signals done, fails with
     * AgentTimeoutException on wall-clock timeout, or fails with
     * AgentProcessException on subprocess error.
     *
     * Subscriber-driven cancellation (unsubscribing from the Multi) is also
     * supported and triggers subprocess cleanup — no exception is raised.
     */
    Multi<AgentEvent> invoke(AgentSessionConfig config);
}
```

### `AgentEvent`

```java
public sealed interface AgentEvent permits AgentEvent.TextDelta {

    /**
     * A streaming text chunk. Each emission is a token-level delta — NOT a buffered
     * complete response. Consumers building a full response must accumulate deltas.
     *
     * Absent intentionally:
     * - ToolCall: Claude Code tool invocations happen inside the CLI subprocess and
     *   are not observable from outside.
     * - UsageReport: SDK cost/token metadata (ResultMessage) is out of scope for v1.
     *   Add as AgentEvent.UsageReport when a concrete consumer requires it.
     */
    record TextDelta(String text) implements AgentEvent {}
}
```

**Terminal state belongs in stream termination, not in event items:**
- Normal completion → `Multi` completes (onComplete)
- Timeout → `Multi` fails with `AgentTimeoutException`
- Subprocess error → `Multi` fails with `AgentProcessException(cause)`

Callers use standard Mutiny idioms:

```java
agentProvider.invoke(config)
    .onFailure(AgentTimeoutException.class).recoverWithItem(gracefulFallback)
    .onFailure(AgentProcessException.class).invoke(e -> log.error("subprocess failed", e))
    .subscribe().with(delta -> buffer.append(delta.text()), Multi::createFrom);
```

### `AgentSessionConfig`

```java
public record AgentSessionConfig(
    String systemPrompt,
    String userPrompt,
    List<AgentMcpServer> mcpServers,
    @Nullable Duration timeout,      // null → ClaudeAgentProperties.defaultTimeout()
    @Nullable String correlationId   // optional; logged via MDC for production tracing
) {
    /** Minimal — default timeout, no MCP servers, no correlation. */
    public static AgentSessionConfig of(String systemPrompt, String userPrompt) {
        return new AgentSessionConfig(systemPrompt, userPrompt, List.of(), null, null);
    }

    /** Explicit timeout. */
    public static AgentSessionConfig of(String systemPrompt, String userPrompt,
                                        Duration timeout) {
        return new AgentSessionConfig(systemPrompt, userPrompt, List.of(), timeout, null);
    }

    /** Full config. */
    public static AgentSessionConfig of(String systemPrompt, String userPrompt,
                                        List<AgentMcpServer> mcpServers,
                                        Duration timeout, String correlationId) {
        return new AgentSessionConfig(systemPrompt, userPrompt, mcpServers,
                                      timeout, correlationId);
    }
}
```

`userPrompt` is required and non-null — single-shot invocations always have a user
prompt. `Optional<Duration>` is not used — it is a return-type idiom and does not
belong as a record component.

### `AgentMcpServer`

Our own sealed interface — keeps `agent-api/` free of the SDK dep. `agent-claude/`
converts to SDK `McpServerConfig` types at invocation time.

```java
public sealed interface AgentMcpServer
    permits AgentMcpServer.Stdio, AgentMcpServer.Sse, AgentMcpServer.Http {

    /**
     * env behaviour: merged OVER the parent process environment (not a replacement).
     * PATH and system variables are preserved. To unset a parent var, set it to "".
     */
    record Stdio(String command, List<String> args, Map<String, String> env)
        implements AgentMcpServer {
        public Stdio(String command) { this(command, List.of(), Map.of()); }
        public Stdio(String command, List<String> args) { this(command, args, Map.of()); }
    }

    record Sse(String url, Map<String, String> headers) implements AgentMcpServer {
        public Sse(String url) { this(url, Map.of()); }
    }

    record Http(String url, Map<String, String> headers) implements AgentMcpServer {
        public Http(String url) { this(url, Map.of()); }
    }
}
```

### Typed exceptions

```java
/** Wall-clock session timeout exceeded. */
public class AgentTimeoutException extends RuntimeException {
    public AgentTimeoutException(Duration timeout) {
        super("Agent session exceeded wall-clock timeout of " + timeout);
    }
}

/** Claude CLI subprocess error. */
public class AgentProcessException extends RuntimeException {
    public AgentProcessException(String message, Throwable cause) {
        super(message, cause);
    }
}

/** Concurrent session cap reached. Thrown synchronously before Multi is returned. */
public class AgentSessionLimitException extends RuntimeException {
    public AgentSessionLimitException(int limit) {
        super("Agent session limit reached (" + limit + " concurrent sessions). " +
              "Set casehub.platform.agent.claude.max-concurrent-sessions to increase.");
    }
}
```

---

## platform/ — NoOpAgentProvider

Lives in the existing `platform/` module alongside `NoOpCaseMemoryStore` and the
other `@DefaultBean` no-ops.

```java
@DefaultBean
@ApplicationScoped
public class NoOpAgentProvider implements AgentProvider {

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        return Multi.createFrom().empty();
    }
}
```

Active when `agent-claude/` is not on the classpath (dev mode, `@QuarkusTest` without
Claude CLI). Downstream tests that need specific agent responses use `@InjectMock
AgentProvider` and stub `invoke()` with test fixtures.

---

## agent-claude/ — Claude Agent SDK Integration

**Dependencies:** `agent-api/`, `org.springaicommunity:claude-code-sdk:1.0.0`,
`platform/` (for Quarkus CDI), Mutiny.

**Maven coordinate note:** The originating issue listed
`com.github.spring-ai-community:claude-agent-sdk-java:1.0.0` — this is wrong.
The correct coordinate confirmed on Maven Central is
`org.springaicommunity:claude-code-sdk:1.0.0`.

### `ClaudeAgentProvider`

The `@Alternative @Priority(1)` implementation. Activates by classpath presence of
`agent-claude/`.

```java
@Alternative
@Priority(1)
@ApplicationScoped
public class ClaudeAgentProvider implements AgentProvider {

    @Inject ClaudeAgentClient client;

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        return client.run(config);
    }
}
```

### `ClaudeAgentClient`

Implementation detail inside `agent-claude/`. Not injected directly by app code.

```java
@ApplicationScoped
public class ClaudeAgentClient {

    /**
     * Invoke the Claude CLI, stream TextDelta events until completion or timeout.
     *
     * Threading: subscription is shifted to the worker pool via
     * runSubscriptionOn(Infrastructure.getDefaultWorkerPool()). Safe to call from
     * Vert.x IO threads and reactive handlers. Required — the SDK makes blocking
     * subprocess calls during subscription. Without the shift, subscription runs on
     * the calling thread, violating spi-reactive-blocking-io.md (PP-20260529-9f9627).
     *
     * Timeout: wall-clock, not idle. Fires after config.timeout() (or
     * defaultTimeout() if null) from subscription time, regardless of whether items
     * are flowing. Fails with AgentTimeoutException.
     *
     * @throws AgentSessionLimitException before returning Multi if cap is reached
     */
    public Multi<AgentEvent> run(AgentSessionConfig config) {
        if (!semaphore.tryAcquire()) {
            throw new AgentSessionLimitException(properties.maxConcurrentSessions());
        }
        try {
            Multi<AgentEvent> events = buildEventStream(config)
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onCompletion().invoke(semaphore::release)
                .onFailure().invoke(t -> semaphore.release())
                .onCancellation().invoke(semaphore::release);
            return applyTimeout(events, config);
        } catch (Exception e) {
            semaphore.release();
            throw e;
        }
    }
}
```

**Semaphore:** `Semaphore(maxConcurrentSessions)`, non-blocking `tryAcquire()`.
All three termination paths must be covered — completion, failure, and subscriber-
driven cancellation. One missed path silently exhausts the pool permanently.
The `try/catch` around stream construction ensures the semaphore is released even
if `buildEventStream()` throws before the Multi is returned.

**Reactor → Mutiny bridge:**
```java
Multi.createFrom().publisher(sdkFlux).map(this::toAgentEvent)
```

**Timeout:** wall-clock timeout applied to the composed pipeline. The correct Mutiny
operator is not `.ifNoItem().after()` (that is idle timeout). Use a wall-clock racing
approach — merge the event stream with a `Multi` that fails with `AgentTimeoutException`
after the configured duration, then take first-to-terminate.

**correlationId:** if `config.correlationId()` is non-null, set in MDC at stream start
and remove at all termination paths (completion, failure, cancellation).

**`@PreDestroy`:** cancels all active subprocess handles tracked in a
`CopyOnWriteArrayList`. Sessions that complete normally before shutdown are unaffected.

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

**Startup validation:** `@PostConstruct` verifies the claude binary is resolvable
(via `binaryPath` or `PATH`). Throws if missing — fail fast at startup, not per-call.
Authentication is not checked at startup (requires network); unauthenticated CLI
surfaces as `AgentProcessException` at invocation time.

---

## Operational Prerequisites

| Requirement | Failure mode |
|---|---|
| `claude` binary on PATH (or `binaryPath` set) | Startup failure — `@PostConstruct` |
| Claude authenticated (`ANTHROPIC_API_KEY` or session) | Per-call `AgentProcessException` |
| Subprocess memory available | Per-call `AgentProcessException` or OS kill |
| Container/CI: binary in image/environment | Startup failure |

For environments without Claude CLI (CI unit tests, `@QuarkusTest`), do not add
`agent-claude/` to the test classpath. Use `NoOpAgentProvider` (active by default)
or `@InjectMock AgentProvider`.

---

## CDI Priority Pattern

This follows the classpath-presence activation mechanism from
`persistence-backend-cdi-priority.md`, applied as a two-tier model (not three).
There is no intermediate primary tier — only NoOp (default) and Claude (alternative).

```java
// LangChain4j — @DefaultBean, works with any LLM, always runs in CI
@DefaultBean
@ApplicationScoped
public class LangChain4jDebateAgentProvider implements DebateAgentProvider {
    @RegisterAiService interface DebateAiService { ... }
    @Inject DebateAiService aiService;
    // implements DebateAgentProvider using aiService
}

// Claude — activates when agent-claude/ is on the compile classpath
@Alternative @Priority(1)
@ApplicationScoped
public class ClaudeDebateAgentProvider implements DebateAgentProvider {
    @Inject AgentProvider agentProvider;  // NOT ClaudeAgentClient
    // implements DebateAgentProvider using agentProvider.invoke(config)
}
```

App code injects `DebateAgentProvider` (the domain SPI). The CDI runtime selects the
active implementation based on classpath. No registration call. No conditional bean.

---

## Testing Pattern

### Integration tests

ITs that invoke the real Claude CLI are opt-in via environment variable — compatible
with library modules that omit `quarkus:build`:

```java
@EnabledIfEnvironmentVariable(named = "CLAUDE_AGENT_TESTS_ENABLED", matches = "true")
class ClaudeAgentClientIT {
    @Inject AgentProvider agentProvider;
    // ...
}
```

Set `CLAUDE_AGENT_TESTS_ENABLED=true` locally when Claude CLI is installed and
authenticated. CI runs without it.

### Parity contract tests

The contract test pattern is an **application-repo convention**, not a platform
artifact. The abstract base class and both concrete tests live in the app repo
alongside the domain SPI:

```java
// In drafthouse — abstract contract, tests structural correctness
public abstract class DebateAgentProviderContractTest {
    protected abstract DebateAgentProvider provider();
    @Test void outputHasRequiredShape() { ... }
}

// LangChain4j — always runs in CI
class LangChain4jDebateAgentProviderTest extends DebateAgentProviderContractTest { ... }

// Claude — opt-in locally
@EnabledIfEnvironmentVariable(named = "CLAUDE_AGENT_TESTS_ENABLED", matches = "true")
class ClaudeDebateAgentProviderTest extends DebateAgentProviderContractTest { ... }
```

Reasoning quality is out of CI scope. Contract tests verify output shape and required
fields only.

---

## Protocol: ai-agent-provider-cdi-priority

Captured now — target: `casehub/garden/docs/protocols/universal/`.

> **Rule:** When an application domain SPI has both a LangChain4j implementation
> (any LLM, portable, works in CI) and a Claude Agent SDK implementation (Claude
> only, requires CLI), apply classpath-presence activation:
>
> - `@DefaultBean @ApplicationScoped` — LangChain4j implementation, always active
> - `@Alternative @Priority(1) @ApplicationScoped` — Claude implementation, activates
>   when `casehub-platform-agent-claude` is on the compile classpath
>
> This is a two-tier model. The Claude implementation injects `AgentProvider` (the
> platform SPI), not `ClaudeAgentClient` directly.
>
> Both implementations must pass the domain SPI's parity contract tests.
> Reasoning quality is out of CI scope; test structural correctness only.
>
> Reference: casehubio/platform#55, spec 2026-06-02.

---

## Out of Scope for v1

- **AgentSession / multi-turn:** Deferred. No current use case requires it. Subprocess-
  held state has no crash-recovery path. State persistence belongs in `CaseMemoryStore`.
  Design when a concrete multi-turn use case emerges.
- **In-process MCP tools (McpSdkServerConfig):** Primary use cases inject context into
  the prompt. Revisit if an open-ended investigation use case requires Claude to query
  application data on demand mid-task.
- **ToolCall events:** Claude Code tool invocations are opaque to the observer.
- **UsageReport / cost metadata:** Add as `AgentEvent.UsageReport` when a consumer
  requires it.
- **Multi-model support:** `ClaudeAgentProperties` targets Claude CLI only. Other models
  use the LangChain4j `@DefaultBean` path.

---

## Downstream Migration (post-ship)

Open a tracking issue per repo after this module ships. Priority order:

1. `casehubio/drafthouse` — originating use case (`DebateAgentProvider`)
2. `casehubio/eidos` — `SystemPromptRenderer` with optional `ChatModel`
3. `casehubio/engine` — `casehub-engine-ai` embedding and routing
4. `casehubio/devtown`, `casehubio/aml`, `casehubio/clinical` — confirm LLM usage first

`casehubio/openclaw`: tmux-based `WorkerProvisioner` already wraps Claude Code —
assess whether `ClaudeAgentProvider` is additive or redundant before opening.

---

## References

- casehubio/platform#55 — originating issue (dep analysis + design decisions)
- casehubio/drafthouse#29 — originating use case
- Maven Central: `org.springaicommunity:claude-code-sdk:1.0.0`
- `persistence-backend-cdi-priority.md` — classpath-presence activation pattern
- `spi-reactive-blocking-io.md` (PP-20260529-9f9627) — Vert.x IO thread rule
- `spi-adapter-module-placement.md` — module in platform; extract if cross-org adoption
