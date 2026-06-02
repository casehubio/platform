# casehub-platform-agent — Design Spec

**Date:** 2026-06-02 (revised after systematic review)
**Issue:** casehubio/platform#55
**Status:** Two architectural decisions pending — see §AgentSession and §AgentProvider SPI

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

**Reactor transitive dependency:** `org.springaicommunity:claude-code-sdk:1.0.0` pulls
in `reactor-core 3.6.10`. `io.modelcontextprotocol.sdk:mcp:0.15.0` (transitive through
the SDK) pulls in `reactor-core 3.7.0`. Maven resolves to 3.7.0. Quarkus 3.32.2 does
not manage `reactor-core` in its BOM — no conflict. Both Mutiny and Reactor will be on
the classpath of any app that depends on `agent-claude/`. This is intentional and
documented.

---

## agent-api/ — SPI Types

**Package:** `io.casehub.platform.agent`
**Dependencies:** Mutiny, slf4j only. No Quarkus, no claude-code-sdk, no casehubio imports.

### `AgentEvent`

```java
public sealed interface AgentEvent permits AgentEvent.TextDelta {

    /**
     * A streaming text chunk from the agent. Each emission is a token-level delta,
     * NOT a complete buffered response. Callers building a full response must
     * accumulate deltas.
     *
     * Claude Code tool invocations happen inside the CLI subprocess and are not
     * observable as events — ToolCall is intentionally absent. Usage/cost metadata
     * is also absent in v1; it can be added as a UsageReport event type once a
     * concrete consumer use case drives the design.
     */
    record TextDelta(String text) implements AgentEvent {}
}
```

**Terminal state belongs in stream termination, not in event items:**
- Normal completion → `Multi` completes (onComplete)
- Timeout → `Multi` fails with `AgentTimeoutException`
- Subprocess error → `Multi` fails with `AgentProcessException(cause)`
- Session cancelled → `Multi` fails with `AgentCancelledException`

Callers use standard Mutiny idioms: `.onFailure(AgentTimeoutException.class).recoverWithItem(...)`,
`.onCompletion().invoke(...)`. No `instanceof` guards needed to detect session end.

### `AgentSessionConfig`

```java
public record AgentSessionConfig(
    String systemPrompt,
    String userPrompt,
    List<AgentMcpServer> mcpServers,
    @Nullable Duration timeout,      // null = use ClaudeAgentProperties.defaultTimeout()
    @Nullable String correlationId   // optional; logged via MDC for production tracing
) {
    /** Single-shot with default timeout and no MCP servers. */
    public static AgentSessionConfig of(String systemPrompt, String userPrompt) {
        return new AgentSessionConfig(systemPrompt, userPrompt, List.of(), null, null);
    }

    /** Single-shot with explicit timeout. */
    public static AgentSessionConfig of(String systemPrompt, String userPrompt, Duration timeout) {
        return new AgentSessionConfig(systemPrompt, userPrompt, List.of(), timeout, null);
    }

    /** Full config. */
    public static AgentSessionConfig of(String systemPrompt, String userPrompt,
                                        List<AgentMcpServer> mcpServers,
                                        Duration timeout, String correlationId) {
        return new AgentSessionConfig(systemPrompt, userPrompt, mcpServers, timeout, correlationId);
    }
}
```

`Optional<Duration>` is not used — Optional is a return-type idiom and does not belong
as a record component. Static factories make intent explicit at the call site.

### `AgentMcpServer`

Our own sealed interface — keeps `agent-api/` free of the SDK dep. `agent-claude/`
converts to SDK `McpServerConfig` types at session start.

```java
public sealed interface AgentMcpServer
    permits AgentMcpServer.Stdio, AgentMcpServer.Sse, AgentMcpServer.Http {

    /**
     * Stdio-based MCP server — launched as a subprocess.
     *
     * env behaviour: the map is MERGED over the parent process's environment
     * (not a replacement). PATH and system variables are preserved. This matches
     * the SDK's McpStdioServerConfig behaviour. Callers who need to unset a parent
     * env var must set it to an empty string.
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
public class AgentTimeoutException extends RuntimeException {
    public AgentTimeoutException(Duration timeout) {
        super("Agent session exceeded wall-clock timeout of " + timeout);
    }
}

public class AgentProcessException extends RuntimeException {
    public AgentProcessException(String message, Throwable cause) {
        super(message, cause);
    }
}

public class AgentCancelledException extends RuntimeException {
    public AgentCancelledException() { super("Agent session was cancelled"); }
}

public class AgentSessionLimitException extends RuntimeException {
    public AgentSessionLimitException(int limit) {
        super("Agent session limit reached (" + limit + " concurrent sessions). " +
              "Set casehub.platform.agent.claude.max-concurrent-sessions to increase.");
    }
}
```

---

### ⚠ PENDING DECISION — AgentProvider SPI (#16)

The review identifies that `ClaudeAgentClient` is a concrete class with no SPI —
downstream `@QuarkusTest` has nothing to inject without a real Claude CLI. The fix
is an `AgentProvider` SPI in `agent-api/` with a `NoOpAgentProvider @DefaultBean`
in `platform/`:

```java
// agent-api/
public interface AgentProvider {
    Multi<AgentEvent> invoke(AgentSessionConfig config);
}

// platform/ — no-op for @QuarkusTest; active when agent-claude/ is not on classpath
@DefaultBean @ApplicationScoped
public class NoOpAgentProvider implements AgentProvider {
    @Override public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        return Multi.createFrom().empty();
    }
}

// agent-claude/
@Alternative @Priority(1) @ApplicationScoped
public class ClaudeAgentProvider implements AgentProvider {
    @Inject ClaudeAgentClient client;
    @Override public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        return client.run(config);
    }
}
```

App code would inject `AgentProvider`, not `ClaudeAgentClient`. `ClaudeAgentClient`
becomes an implementation detail inside `agent-claude/`. This also resolves the
CDI priority pattern reference — the three-tier persistence model doesn't apply here;
the correct model is two tiers: NoOp @DefaultBean + Claude @Alternative @Priority(1).

**Awaiting user confirmation before updating module structure and CDI section.**

---

### ⚠ PENDING DECISION — AgentSession / multi-turn (#17)

The review makes a strong case for deferring `AgentSession` to v2:

1. drafthouse#29 (the originating use case) is a single invocation — no follow-up turns.
2. Multi-turn via subprocess is fragile: subprocess crash = lost conversational state,
   no recovery path. Single-shot + `CaseMemoryStore` is the platform's model for state.
3. Deferring eliminates a significant surface area: `AgentSession`, `close()` blocking
   semantics, query() concurrency contract, session state machine, semaphore leak risk
   for long-lived session objects, and the null `userPrompt` smell.

If accepted: `connect()` is removed. `run()` (or `AgentProvider.invoke()` if #16 is
accepted) is the only API. `AgentSession` is dropped from `agent-api/`.

If multi-turn is retained: the following contracts must be added:
- `query()` while a prior `Multi` is being consumed throws `IllegalStateException`
  (serial model — one turn at a time)
- State machine: CONNECTED → ACTIVE (query running) → CONNECTED → ... → CLOSED/CANCELLED
- After `cancel()`: `query()` throws `IllegalStateException`; state is CANCELLED
- After `close()`: session is not reusable; state is CLOSED
- `close(Duration maxWait)` (not unbounded `close()`) — blocks at most `maxWait` then
  cancels. `AutoCloseable.close()` maps to `close(Duration.ofSeconds(30))` as a bound.
- Semaphore release: three explicit paths — `onCompletion()`, `onFailure()`, 
  `onCancellation()` — all must release. One missed path silently exhausts the pool.
  Session objects that escape closure leak a semaphore slot permanently until `@PreDestroy`.

**Awaiting user confirmation before updating the API section.**

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
     * Single-shot invocation. Streams TextDelta events until the agent signals
     * completion or the wall-clock timeout is reached. No session object — subscribe
     * and consume.
     *
     * Threading: subscription is shifted to the worker pool via
     * runSubscriptionOn(Infrastructure.getDefaultWorkerPool()). Safe to call from
     * Vert.x IO threads and reactive handlers.
     *
     * Timeout: stream fails with AgentTimeoutException after the configured wall-clock
     * duration. Not an idle timeout — the clock starts when subscribe() is called,
     * regardless of whether items are flowing.
     *
     * @throws AgentSessionLimitException immediately (before returning Multi) if the
     *         concurrent session cap is reached
     */
    public Multi<AgentEvent> run(AgentSessionConfig config) { ... }
}
```

**Concurrency cap:** enforced by `Semaphore(maxConcurrentSessions)` with a non-blocking
`tryAcquire()`. If acquisition fails, `AgentSessionLimitException` is thrown before the
`Multi` is returned. Semaphore release covers all three termination paths:

```java
return multi
    .onCompletion().invoke(semaphore::release)
    .onFailure().invoke(t -> semaphore.release())
    .onCancellation().invoke(semaphore::release);
```

All three paths must be covered. One missed path silently exhausts the pool.

**Reactor → Mutiny bridging:**
```java
Multi<AgentEvent> events = Multi.createFrom()
    .publisher(sdkFlux)
    .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
    .map(this::toAgentEvent);
```

`runSubscriptionOn` is mandatory — the SDK makes blocking subprocess calls during
subscription setup. Without it, subscription runs on the calling thread (potentially
the Vert.x IO thread), violating the reactive blocking I/O protocol
(PP-20260529-9f9627, `spi-reactive-blocking-io.md`).

**Timeout:** wall-clock, not idle. The timeout fires after `config.timeout()` (or
`ClaudeAgentProperties.defaultTimeout()` if null) elapsed from subscription time,
regardless of whether events are flowing. Failure path emits `AgentTimeoutException`.

**Startup check:** `@PostConstruct` validates that the claude binary is resolvable
(via `binaryPath` config or `PATH`). If missing, throws at startup — fail fast rather
than per-call runtime errors. Authentication is not checked at startup (requires
network); an unauthenticated CLI will surface as `AgentProcessException` at invocation
time.

**Correlating sessions:** if `config.correlationId()` is non-null, it is set in MDC
at subscription start and removed at termination. Log entries within the session carry
the correlation ID automatically.

**`@PreDestroy`:** cancels all tracked active sessions (cancel semantics, not
graceful). Sessions that complete normally before shutdown are not affected.

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

### Operational prerequisites

| Requirement | Failure mode |
|-------------|-------------|
| `claude` binary on PATH (or `binaryPath` set) | Startup failure — `@PostConstruct` throws |
| Claude authenticated (`ANTHROPIC_API_KEY` or session) | Per-call `AgentProcessException` |
| Sufficient memory for subprocess(es) | Per-call `AgentProcessException` or OS-level kill |
| Container: binary must be in the image | Startup failure |
| CI: binary must be installed in CI environment | Startup failure (if agent-claude/ on classpath) |

For environments where the Claude CLI is not present (CI, unit tests), add
`agent-claude/` only in compile scope for the production profile, or rely on
the `NoOpAgentProvider @DefaultBean` (see AgentProvider SPI decision).

---

## CDI Priority Pattern

The LangChain4j + Claude SDK pattern for application domain SPIs uses classpath-presence
activation. This follows the same mechanism as `persistence-backend-cdi-priority.md`
but applies two tiers rather than three — there is no intermediate "primary" tier;
only NoOp (default) and Claude (alternative):

```java
// LangChain4j — default implementation, works with any LLM, always in CI
@DefaultBean
@ApplicationScoped
public class LangChain4jDebateAgentProvider implements DebateAgentProvider {
    @RegisterAiService interface DebateAiService { ... }
    @Inject DebateAiService aiService;
}

// Claude — activates when agent-claude/ is on the classpath
@Alternative @Priority(1)
@ApplicationScoped
public class ClaudeDebateAgentProvider implements DebateAgentProvider {
    // Inject AgentProvider (not ClaudeAgentClient) if #16 is accepted
    // Inject ClaudeAgentClient directly if #16 is not accepted
}
```

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
}
```

### Dev/test mock path

`ClaudeAgentClient` is a concrete class — downstream `@QuarkusTest` has nothing to
inject without a real Claude CLI. Two strategies:

**If AgentProvider SPI (#16) is adopted:** `NoOpAgentProvider @DefaultBean` in
`platform/` provides an empty stream. Apps inject `AgentProvider` and their tests work
without configuration.

**If AgentProvider SPI is not adopted:** downstream apps must `@InjectMock ClaudeAgentClient`
in test classes and stub `run()` to return `Multi.createFrom().empty()` or test
fixtures. The spec for each app should document this explicitly.

### Contract tests

The parity contract test pattern (shared abstract base class, both implementations
tested against the same structural cases) is an **application-repo convention**, not
a platform artifact. Platform domain types (e.g. `DebateAgentProvider`) do not exist
in this repo. The platform spec describes the pattern; the actual abstract test class
lives in the application repo alongside the domain SPI it tests.

Pattern (implemented in the app repo):

```java
// In drafthouse test sources
public abstract class DebateAgentProviderContractTest {
    protected abstract DebateAgentProvider provider();

    @Test void outputHasRequiredShape() { ... }
    @Test void handlesEmptyManifest() { ... }
}

// LangChain4j — always runs in CI
class LangChain4jDebateAgentProviderTest extends DebateAgentProviderContractTest { ... }

// Claude — opt-in locally
@EnabledIfEnvironmentVariable(named = "CLAUDE_AGENT_TESTS_ENABLED", matches = "true")
class ClaudeDebateAgentProviderTest extends DebateAgentProviderContractTest { ... }
```

---

## Protocol: ai-agent-provider-cdi-priority

**Captured now** (not post-ship). Target: `casehub/garden/docs/protocols/universal/`.

```
ID: (assigned on capture)
Title: AI Agent Provider CDI Priority Pattern

When an application domain SPI has both a LangChain4j implementation (any LLM,
portable, works in CI without special tooling) and a Claude Agent SDK implementation
(full tool access, Claude only, requires CLI), apply the classpath-presence activation
pattern:

  @DefaultBean @ApplicationScoped LangChain4jXxxProvider — always active
  @Alternative @Priority(1) @ApplicationScoped ClaudeXxxProvider — activates when
      casehub-platform-agent-claude is on the compile classpath

This is a two-tier pattern (not the three-tier persistence pattern). There is no
intermediate "primary" tier.

Both implementations must pass the domain SPI's parity contract tests. Structural
correctness is tested in CI; reasoning quality is explicitly out of CI scope.

Reference: platform#55, agent module design spec 2026-06-02.
```

---

## Out of Scope for v1

- **In-process MCP tools:** The primary casehub agent use case injects context into the
  prompt upfront. Claude does not need to call back into the JVM mid-task. Revisit when
  a concrete open-ended investigation use case emerges.
- **Claude Code tool events (`ToolCall`):** Tool invocations happen inside the CLI
  subprocess and are not observable. Absent by design.
- **Usage/cost metadata:** The SDK's `ResultMessage` carries cost data. Not exposed in
  v1 — add as `AgentEvent.UsageReport` when a concrete consumer requires it.
- **Multi-model support:** `ClaudeAgentProperties` targets Claude CLI only. Other models
  are served via the LangChain4j `@DefaultBean` path.
- **AgentSession / multi-turn:** See pending decision §. If deferred, add in v2 when
  a concrete multi-turn use case drives the requirements.

---

## Downstream Migration (post-ship)

After this module ships, open a tracking issue in each repo to review their AI agent
integration and adopt the CDI priority pattern where the Claude SDK alternative adds
value. Priority order:

1. `casehubio/drafthouse` — originating use case, `DebateAgentProvider`
2. `casehubio/eidos` — `SystemPromptRenderer` with optional `ChatModel`
3. `casehubio/engine` — `casehub-engine-ai` embedding and routing
4. `casehubio/devtown`, `casehubio/aml`, `casehubio/clinical` — confirm LLM usage first

`casehubio/openclaw`: assess whether `ClaudeAgentClient` is additive or redundant given
the existing tmux-based `WorkerProvisioner` before opening a migration issue.

---

## References

- casehubio/platform#55 — originating issue (dep analysis + design decisions)
- casehubio/drafthouse#29 — originating use case
- Maven Central: `org.springaicommunity:claude-code-sdk:1.0.0`
- `persistence-backend-cdi-priority.md` — classpath-presence activation pattern
- `spi-reactive-blocking-io.md` (PP-20260529-9f9627) — Vert.x IO thread rule
- `spi-adapter-module-placement.md` — module in platform; extract if cross-org adoption
