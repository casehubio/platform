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

- `casehub-platform-agent-api` — `AgentProvider` SPI + all API types
- `casehub-platform-agent-claude` — Claude Agent SDK Quarkus integration

**Originating use case:** casehubio/drafthouse#29 — `DebateAgentProvider`.

**Architectural decisions:**
- Single-shot only (v1). Multi-turn deferred — see platform#58.
- `AgentProvider` SPI in `agent-api/`; `NoOpAgentProvider @DefaultBean` in `platform/`.
  Apps inject the SPI, never `ClaudeAgentClient` directly.

---

## Module Structure

Flat modules at repo root — `memory-jpa/`, `memory-inmem/` naming convention.
No aggregator POM.

```
agent-api/     casehub-platform-agent-api     — SPI types + exceptions (Mutiny, no Quarkus)
agent-claude/  casehub-platform-agent-claude  — Claude Agent SDK Quarkus integration
```

### Root POM module ordering

Maven reactor resolves build order from declared `<dependency>` relationships, not
`<module>` list order. The ordering below is defensive convention:

```xml
<module>platform-api</module>
<module>agent-api</module>      <!-- before platform/ — platform depends on it -->
<module>platform</module>
<module>testing</module>
<!-- ... other existing modules ... -->
<module>agent-claude</module>   <!-- last — depends on agent-api and platform -->
```

### agent-api/pom.xml

```xml
<dependencies>
    <dependency>
        <groupId>io.smallrye.reactive</groupId>
        <artifactId>mutiny</artifactId>          <!-- version managed by BOM -->
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>

<build><plugins>
    <!-- Required: ARC resolves AgentProvider supertype from this JAR's index
         when ClaudeAgentProvider implements it in agent-claude/. Without the
         index, CDI wiring silently fails at deployment. -->
    <plugin>
        <groupId>io.smallrye</groupId>
        <artifactId>jandex-maven-plugin</artifactId>
        <version>${jandex-maven-plugin.version}</version>
        <executions><execution>
            <id>make-index</id><goals><goal>jandex</goal></goals>
        </execution></executions>
    </plugin>
</plugins></build>
```

No Quarkus. No CDI annotations. No `quarkus-maven-plugin`.

### agent-claude/pom.xml

```xml
<dependencies>
    <dependency>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-platform-agent-api</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-arc</artifactId>   <!-- CDI + SmallRye Config for @ConfigMapping -->
    </dependency>
    <dependency>
        <groupId>org.springaicommunity</groupId>
        <artifactId>claude-code-sdk</artifactId>
        <version>1.0.0</version>               <!-- NOT in BOM — explicit version required -->
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-junit5</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>

<build><plugins>
    <plugin>
        <groupId>io.smallrye</groupId>
        <artifactId>jandex-maven-plugin</artifactId>
        <version>${jandex-maven-plugin.version}</version>
        <executions><execution>
            <id>make-index</id><goals><goal>jandex</goal></goals>
        </execution></executions>
    </plugin>
    <!-- No build goal — library module (same as identity/, scim/) -->
    <plugin>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-maven-plugin</artifactId>
        <version>${quarkus.platform.version}</version>
        <extensions>true</extensions>
        <executions><execution>
            <goals>
                <goal>generate-code</goal>
                <goal>generate-code-tests</goal>
            </goals>
        </execution></executions>
    </plugin>
</plugins></build>
```

No additional deps for the `ScheduledExecutorService` timer — `java.util.concurrent`
is JDK standard. No Vert.x dep needed.

### platform/pom.xml change

Add one compile dependency for `NoOpAgentProvider`:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-agent-api</artifactId>
    <version>${project.version}</version>
</dependency>
```

`platform/` already has Jandex and `quarkus:build` — no other POM changes needed.

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

### Reactor transitive dependency

`claude-code-sdk:1.0.0` → `reactor-core 3.6.10`; `mcp:0.15.0` (transitive) → `reactor-core
3.7.0`. Maven resolves to 3.7.0. Quarkus 3.32.2 does not manage `reactor-core`. Both
Mutiny and Reactor on the classpath of any app taking `agent-claude/`. Expected.

---

## agent-api/ — SPI Types

**Package:** `io.casehub.platform.agent`
**Dependencies:** Mutiny only. No Quarkus, no CDI, no claude-code-sdk, no casehubio imports.

### `AgentProvider`

```java
/**
 * Platform SPI for single-shot AI agent invocation.
 *
 * <p>Implementations should be {@code @ApplicationScoped} — agent infrastructure
 * is shared across requests.
 */
public interface AgentProvider {
    /**
     * Invoke the agent and stream response events.
     *
     * The returned Multi completes when the agent signals done.
     * Fails with AgentTimeoutException on wall-clock timeout.
     * Fails with AgentProcessException on subprocess error.
     * Subscriber cancellation triggers subprocess cleanup — no exception raised.
     */
    Multi<AgentEvent> invoke(AgentSessionConfig config);
}
```

### `AgentEvent`

```java
public sealed interface AgentEvent permits AgentEvent.TextDelta {

    /**
     * A token-level streaming chunk. NOT a buffered complete response.
     * Accumulate deltas to build the full response.
     *
     * Absent intentionally:
     * - ToolCall: Claude Code tool invocations are opaque to the observer.
     * - UsageReport: SDK cost metadata out of scope for v1.
     */
    record TextDelta(String text) implements AgentEvent {}
}
```

Terminal state via stream termination only:
- Normal completion → `Multi` completes
- Timeout → `Multi` fails with `AgentTimeoutException`
- Subprocess error → `Multi` fails with `AgentProcessException(cause)`

### `AgentSessionConfig`

```java
public record AgentSessionConfig(
    String systemPrompt,
    String userPrompt,
    List<AgentMcpServer> mcpServers,
    Duration timeout,        // null → ClaudeAgentProperties.defaultTimeout()
    String correlationId     // null → no correlation logging
) {
    // Compact constructor — enforce non-null on required fields.
    // Defensive copy of mcpServers prevents external mutation.
    public AgentSessionConfig {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(userPrompt, "userPrompt");
        mcpServers = mcpServers != null ? List.copyOf(mcpServers) : List.of();
    }

    /** Default timeout, no MCP, no correlation. */
    public static AgentSessionConfig of(String systemPrompt, String userPrompt) {
        return new AgentSessionConfig(systemPrompt, userPrompt, List.of(), null, null);
    }

    /** Explicit timeout. */
    public static AgentSessionConfig of(String systemPrompt, String userPrompt,
                                        Duration timeout) {
        return new AgentSessionConfig(systemPrompt, userPrompt, List.of(), timeout, null);
    }
}
```

`userPrompt` is required and non-null. `timeout` and `correlationId` are nullable with
documented null contracts. `Optional<Duration>` is not used — return-type idiom only.

### `AgentMcpServer`

```java
public sealed interface AgentMcpServer
    permits AgentMcpServer.Stdio, AgentMcpServer.Sse, AgentMcpServer.Http {

    /**
     * Stdio MCP server: launched as a subprocess by the Claude CLI.
     * env: MERGED over the parent process environment (not a replacement).
     * PATH and system variables are preserved. Set a key to "" to unset it.
     */
    record Stdio(String command, List<String> args, Map<String, String> env)
        implements AgentMcpServer {
        public Stdio(String command) { this(command, List.of(), Map.of()); }
        public Stdio(String command, List<String> args) { this(command, args, Map.of()); }
    }

    /**
     * SSE MCP server: legacy HTTP transport (Server-Sent Events).
     * Prefer Http for new deployments.
     */
    record Sse(String url, Map<String, String> headers) implements AgentMcpServer {
        public Sse(String url) { this(url, Map.of()); }
    }

    /**
     * Streamable HTTP MCP server: current MCP transport standard.
     * Prefer this over Sse for new servers.
     */
    record Http(String url, Map<String, String> headers) implements AgentMcpServer {
        public Http(String url) { this(url, Map.of()); }
    }
}
```

External MCP servers only. In-process Java tool handlers out of scope for v1.

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

public class AgentSessionLimitException extends RuntimeException {
    public AgentSessionLimitException(int limit) {
        super("Agent session limit reached (" + limit + " concurrent sessions). " +
              "Set casehub.platform.agent.claude.max-concurrent-sessions to increase.");
    }
}
```

---

## platform/ — NoOpAgentProvider

```java
@DefaultBean
@ApplicationScoped
public class NoOpAgentProvider implements AgentProvider {

    private static final Logger LOG = Logger.getLogger(NoOpAgentProvider.class);

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        LOG.warn("NoOpAgentProvider is active — add casehub-platform-agent-claude " +
                 "to the classpath to get real Claude output");
        return Multi.createFrom().empty();
    }
}
```

The real agent and the NoOp both complete the Multi normally. The `LOG.warn` is the only
observable distinction for dev misconfiguration. `platform/` already has Jandex and
`quarkus:build`.

---

## agent-claude/ — Claude Agent SDK Integration

**Maven coordinate:** `org.springaicommunity:claude-code-sdk:1.0.0`.
The originating issue listed a different coordinate — incorrect.

### `ClaudeAgentProvider`

```java
@ApplicationScoped   // beats NoOpAgentProvider @DefaultBean automatically
public class ClaudeAgentProvider implements AgentProvider {

    @Inject ClaudeAgentClient client;

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        return client.run(config);
    }
}
```

CDI tier for `AgentProvider`:
- Tier 1: `NoOpAgentProvider @DefaultBean @ApplicationScoped` (platform/) — default
- Tier 2: `ClaudeAgentProvider @ApplicationScoped` (agent-claude/) — active on classpath

`@Alternative @Priority(1)` is not used — that is Tier 3, for secondary backends that
beat a Tier 2 primary. There is no Tier 2 primary; using it prematurely would cause an
ambiguous dependency exception if a second `@Priority(1)` bean were added later.

### `ClaudeAgentClient`

Implementation detail inside `agent-claude/`. App code never injects this directly.

```java
/**
 * @Startup forces eager initialization on the main thread during application startup,
 * before any reactive handlers can run. Without @Startup, ARC initializes lazily on
 * first injection. If that happens on the Vert.x IO thread, @PostConstruct
 * validateBinary() would call ProcessBuilder.start().waitFor() there — blocking the IO
 * thread in violation of spi-reactive-blocking-io.md (PP-20260529-9f9627).
 *
 * Same pattern as PathParserConfigurator and MongoPreferenceIndexes in this module.
 */
@Startup
@ApplicationScoped
public class ClaudeAgentClient {

    private final ClaudeAgentProperties properties;
    private final Semaphore semaphore;
    private final CopyOnWriteArraySet<ClaudeAsyncClient> activeSessions;
    private final ScheduledExecutorService timeoutScheduler;
    // Non-null in tests only — set by test constructor, checked in buildEventStream()
    private final Function<AgentSessionConfig, Multi<AgentEvent>> streamFactory;

    @Inject
    public ClaudeAgentClient(ClaudeAgentProperties properties) {
        this.properties = properties;
        this.semaphore = new Semaphore(properties.maxConcurrentSessions());
        this.activeSessions = new CopyOnWriteArraySet<>();
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "casehub-agent-timeout");
            t.setDaemon(true);
            return t;
        });
        this.streamFactory = null;
    }

    /** Required by ARC for subclass-based proxy generation. Must not be called directly. */
    protected ClaudeAgentClient() {
        this.properties = null;
        this.semaphore = null;
        this.activeSessions = null;
        this.timeoutScheduler = null;
        this.streamFactory = null;
    }

    /**
     * Test constructor — bypasses {@code @PostConstruct}. Call with {@code new} in tests;
     * do not expose to CDI. Prefer this over subclassing — CDI proxies are subclass-based
     * and a test subclass risks firing @PostConstruct if CDI manages the instance.
     * Follows the ScimActorDIDProvider pattern established in this codebase.
     */
    public ClaudeAgentClient(ClaudeAgentProperties properties,
                             Function<AgentSessionConfig, Multi<AgentEvent>> streamFactory) {
        this.properties = properties;
        this.semaphore = new Semaphore(properties.maxConcurrentSessions());
        this.activeSessions = new CopyOnWriteArraySet<>();
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "casehub-agent-timeout");
            t.setDaemon(true);
            return t;
        });
        this.streamFactory = streamFactory;
    }

    @PostConstruct
    void validateBinary() {
        String binary = properties.binaryPath().orElse("claude");
        try {
            Process process = new ProcessBuilder(binary, "--version").start();
            // 10-second bound: --version completes in milliseconds.
            // A hung probe means something is wrong with the install — fail fast.
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException(
                    "claude binary probe timed out after 10s: " + binary);
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IllegalStateException(
                    "claude binary at '" + binary + "' exited with code " + exitCode +
                    " — possible broken installation");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Interrupted while probing claude binary: " + binary, e);
        } catch (IOException e) {
            throw new IllegalStateException(
                "claude binary not found or not executable: " + binary +
                " — configure casehub.platform.agent.claude.binary-path " +
                "or ensure 'claude' is on PATH", e);
        }
        LOG.warnf("claude binary resolved at '%s'. Authentication not verified — " +
                  "AgentProcessException will surface on first invocation if unauthenticated.",
                  binary);
    }

    /**
     * Stream TextDelta events until the agent completes or the wall-clock timeout fires.
     * All error cases are surfaced via onFailure() on the returned Multi — run() never throws.
     *
     * Responsibility split:
     *   buildEventStream() owns: timer cancel, session deregister, sdkClient.close() — on all paths
     *   run() owns: semaphore release — on all paths, as the outer handler layer
     *
     * Threading: subscription shifted to the worker pool via runSubscriptionOn(). The SDK
     * creates a subprocess during subscription setup — this blocks. Without the shift,
     * subscription blocks the Vert.x IO thread, violating PP-20260529-9f9627.
     */
    public Multi<AgentEvent> run(AgentSessionConfig config) {
        if (!semaphore.tryAcquire()) {
            // Semaphore not acquired — return failure directly. No termination handlers
            // registered on this Multi because there is nothing to release.
            return Multi.createFrom().failure(
                new AgentSessionLimitException(properties.maxConcurrentSessions()));
        }
        try {
            // buildEventStream() returns a Multi with cleanup handlers already wired.
            // run() adds semaphore release as the outer layer on top.
            return buildEventStream(config)
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onCompletion().invoke(semaphore::release)
                .onFailure().invoke(t -> semaphore.release())
                .onCancellation().invoke(semaphore::release);
        } catch (Exception e) {
            semaphore.release();
            return Multi.createFrom().failure(e);
        }
    }

    @PreDestroy
    void shutdown() {
        // Shut down timer scheduler first — no new timeouts will fire.
        // Then close all sessions that are still active.
        // Isolate per-client so one failure doesn't abort the rest.
        timeoutScheduler.shutdownNow();
        activeSessions.forEach(c -> {
            try { c.close().subscribe(); } catch (Exception ignored) {}
        });
    }
}
```

### buildEventStream() — architecture

`buildEventStream()` is responsible for SDK client creation, session tracking, timeout
scheduling, and cleanup wiring. It returns a "thick" Multi with all cleanup handlers
already registered. `run()` adds semaphore release as the outer layer on top.

`buildEventStream()` is package-private — called only from `run()` in the same class.
The test constructor bypasses it via `streamFactory`, so its visibility has no test surface.

```
/* package-private */ Multi<AgentEvent> buildEventStream(AgentSessionConfig config):

1. If streamFactory != null (test path): return streamFactory.apply(config)

2. Production path:
   a. Resolve effectiveTimeout (config.timeout() or properties.defaultTimeout())
   b. Create ClaudeAsyncClient per-invocation via ClaudeClient.async() builder:
      - system prompt via CLIOptions.systemPrompt(config.systemPrompt())
      - MCP servers converted from List<AgentMcpServer> to Map<String, McpServerConfig>
   c. Register sdkClient in activeSessions
   d. Declare AtomicBoolean timedOut = new AtomicBoolean(false)
   e. Schedule subprocess closure:
        ScheduledFuture<?> timeoutFuture = timeoutScheduler.schedule(() -> {
            if (timedOut.compareAndSet(false, true)) {
                sdkClient.close().subscribe();  // only fires if not already closed
            }
        }, effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
      compareAndSet(false, true) prevents double-close if completion and timer race.
   f. try {
        Obtain Flux<String> via sdkClient.connect(config.userPrompt()).textStream()
        textStream() emits token-level text deltas — no Message type inspection needed.
        Bridge to Multi<AgentEvent>:
          Multi.createFrom().publisher(textFlux).map(text -> new AgentEvent.TextDelta(text))
        Apply failure transformation — if timedOut, subprocess error from timer-driven
        close() is typed as AgentTimeoutException; otherwise AgentProcessException.
        Throwable.getMessage() may be null — use Objects.toString(e.getMessage(), e.getClass().getSimpleName()):
          .onFailure().transform(e ->
              timedOut.get()
                  ? new AgentTimeoutException(effectiveTimeout)
                  : new AgentProcessException(
                        Objects.toString(e.getMessage(), e.getClass().getSimpleName()), e))
        Wire cleanup on all three termination paths:
          .onCompletion().invoke(() -> { timeoutFuture.cancel(false);
                                         activeSessions.remove(sdkClient);
                                         sdkClient.close().subscribe(); })
          .onFailure().invoke(t -> { timeoutFuture.cancel(false);
                                     activeSessions.remove(sdkClient);
                                     sdkClient.close().subscribe(); })
          .onCancellation().invoke(() -> { timeoutFuture.cancel(false);
                                           activeSessions.remove(sdkClient);
                                           sdkClient.close().subscribe(); })
        sdkClient.close() is idempotent — safe even if already closed by timer.
        Explicit close() on all paths: Flux cancellation does NOT guarantee subprocess
        termination in the SDK implementation.
        return eventStream;
      } catch (Exception e) {
        // buildEventStream() threw synchronously after c and e above.
        // Semaphore is released by run()'s catch block.
        // Clean up timer and session registered before the failure:
        timeoutFuture.cancel(false);
        activeSessions.remove(sdkClient);
        sdkClient.close().subscribe();
        throw e;
      }
```

**Timeout correctness note:** Merge-based timeout approaches (`Multi.createBy().merging()`)
have a correctness flaw: Mutiny's merge completes only when ALL upstream streams complete.
If events completes before the timer fires, the merged stream stays open, then fails with
`AgentTimeoutException` — falsely reporting timeout on a successful session. Scheduled
subprocess closure avoids this by tying the timer to subprocess lifecycle, not stream
composition.

**Correlating sessions:** if `config.correlationId()` is non-null, log it as a structured
field at key lifecycle points (stream start, completion, failure, cancellation) using the
logging framework's structured parameter support. SLF4J MDC is thread-local and does not
propagate across `runSubscriptionOn()` — no MDC-based approach is used.

### `ClaudeAgentProperties`

```java
@ConfigMapping(prefix = "casehub.platform.agent.claude")
public interface ClaudeAgentProperties {
    Optional<String> binaryPath();

    @WithDefault("PT5M")
    Duration defaultTimeout();

    @WithDefault("4")
    int maxConcurrentSessions();
}
```

---

## Operational Prerequisites

| Requirement | Failure mode |
|---|---|
| `claude` binary on PATH (or `binaryPath` configured) | `IllegalStateException` at startup |
| Claude authenticated (`ANTHROPIC_API_KEY` or session) | `AgentProcessException` at first call |
| Subprocess memory available | `AgentProcessException` or OS kill |
| Container/CI: binary in image/environment | `IllegalStateException` at startup |

For environments without Claude CLI: don't add `agent-claude/` to classpath.
`NoOpAgentProvider` is active by default (with `LOG.warn` at invocation time).

---

## CDI Priority Pattern

### AgentProvider (platform SPI)

Classpath-presence activation — two tiers per `persistence-backend-cdi-priority.md`:

| Tier | Annotation | Module | Active when |
|------|-----------|--------|-------------|
| 1 — NoOp | `@DefaultBean @ApplicationScoped` | `platform/` | Default |
| 2 — Claude | `@ApplicationScoped` | `agent-claude/` | `agent-claude/` on classpath |

### Domain SPI pattern — module separation required

**Both implementations of a domain SPI must not live in the same Maven module.**
If `ClaudeDebateAgentProvider @ApplicationScoped` and `LangChain4jDebateAgentProvider
@DefaultBean` are in the same module, CDI always resolves to Claude regardless of
classpath — `@ApplicationScoped` unconditionally beats `@DefaultBean`.
`LangChain4jDebateAgentProvider` becomes dead code. CI never tests it.

**Correct structure for a downstream app (e.g., drafthouse):**

```
drafthouse/
  drafthouse-api/     — DebateAgentProvider SPI (pure Java)
  drafthouse/         — main app; LangChain4jDebateAgentProvider @DefaultBean
  drafthouse-claude/  — NEW module; ClaudeDebateAgentProvider @ApplicationScoped here;
                        declares casehub-platform-agent-claude as compile dep
```

Adding `drafthouse-claude/` to the app's compile deps activates both `agent-claude/`
and `ClaudeDebateAgentProvider`. Removing it falls back to LangChain4j. Per
`optional-module-pattern.md` applied to domain SPIs.

**Domain SPI Claude implementation (in the -claude module):**

```java
@ApplicationScoped
public class ClaudeDebateAgentProvider implements DebateAgentProvider {
    @Inject AgentProvider agentProvider;  // NOT ClaudeAgentClient
    // implement DebateAgentProvider using agentProvider.invoke(config)
}
```

---

## Testing Pattern

### Integration tests (real Claude CLI)

```java
@EnabledIfEnvironmentVariable(named = "CLAUDE_AGENT_TESTS_ENABLED", matches = "true")
class ClaudeAgentClientIT {
    @Inject AgentProvider agentProvider;
}
```

### ClaudeAgentClient infrastructure unit tests

Test infrastructure behavior (semaphore limit, timeout) without a real Claude CLI using
the test constructor. Do not subclass — CDI proxies are subclass-based and a test subclass
risks firing `@PostConstruct` when CDI manages the instance. The test constructor bypasses
`@PostConstruct` entirely (same pattern as `ScimActorDIDProvider`):

```java
// In agent-claude/ test sources
class ClaudeAgentClientTest {

    private ClaudeAgentClient clientWith(Multi<AgentEvent> stream) {
        var props = mock(ClaudeAgentProperties.class);
        when(props.maxConcurrentSessions()).thenReturn(2);
        when(props.defaultTimeout()).thenReturn(Duration.ofMinutes(5));
        return new ClaudeAgentClient(props, config -> stream);
    }

    @Test
    void limitReached_returnsFailureMulti() {
        // Fill cap with slow streams
        var client = clientWith(Multi.createFrom().nothing());
        client.run(config());
        client.run(config());

        // Third call must fail via onFailure (not throw synchronously).
        // collect().asList() on a failure Multi itself fails when awaited.
        var result = client.run(config());
        assertThatThrownBy(() -> result.collect().asList().await().indefinitely())
            .isInstanceOf(AgentSessionLimitException.class);
    }
}
```

The test constructor returns `streamFactory.apply(config)` from `buildEventStream()`.
This covers semaphore limit rejection, timeout propagation, and termination handler
correctness without any subprocess involvement.

### Dev/test mock path for downstream apps

1. **Separate test classpath** — don't add `drafthouse-claude/` to the test module.
2. **`@InjectMock AgentProvider`** — Quarkus CDI mock; stub `invoke()` with fixtures.
3. **Profile-scoped module** — add `drafthouse-claude/` only in a Maven profile.

---

## Protocol: ai-agent-provider-cdi-priority

Captured now — target: `casehub/garden/docs/protocols/universal/`.

> **Rule:** When an application domain SPI has a LangChain4j default and a Claude
> Agent SDK alternative, use classpath-presence activation:
>
> - `@DefaultBean @ApplicationScoped` — LangChain4j in the main app module
> - `@ApplicationScoped` — Claude in a **separate module** that declares
>   `casehub-platform-agent-claude` as a compile dependency
>
> Module separation is required. Both implementations in the same module
> defeats classpath-presence activation: @ApplicationScoped always wins.
>
> Claude implementation injects `AgentProvider` (platform SPI), not `ClaudeAgentClient`.
>
> Both implementations must pass the domain SPI's parity contract tests.
> Structural correctness only in CI; reasoning quality is out of scope.
>
> Reference: casehubio/platform#55, spec 2026-06-02.

---

## Out of Scope for v1

- **AgentSession / multi-turn:** platform#58.
- **In-process MCP tools:** Primary use cases inject context into the prompt.
- **ToolCall events:** Opaque inside the subprocess.
- **UsageReport / cost metadata:** Add as `AgentEvent.UsageReport` when needed.
- **Multi-model support:** Claude CLI only.
- **openclaw migration:** platform#57.
- **PLATFORM.md / CLAUDE.md update:** platform#59.

---

## Downstream Migration (post-ship)

Each adopting app requires a **new Maven module** (e.g., `drafthouse-claude/`)
per `optional-module-pattern.md`. Adding a class to an existing module is insufficient.

Priority order:
1. `casehubio/drafthouse` — originating use case
2. `casehubio/eidos` — `SystemPromptRenderer`
3. `casehubio/engine` — `casehub-engine-ai`
4. `casehubio/devtown`, `casehubio/aml`, `casehubio/clinical` — confirm LLM usage first

---

## References

- casehubio/platform#55 — originating issue
- casehubio/platform#57 — openclaw assessment
- casehubio/platform#58 — AgentSession v2
- casehubio/platform#59 — PLATFORM.md / CLAUDE.md update
- casehubio/drafthouse#29 — originating use case
- Maven Central: `org.springaicommunity:claude-code-sdk:1.0.0`
- `persistence-backend-cdi-priority.md` (PP-20260522-0cfa30)
- `spi-reactive-blocking-io.md` (PP-20260529-9f9627)
- `library-jars-require-jandex.md` (PP-20260601-37179a)
- `optional-module-pattern.md` (PP-20260508-6d1f5c)
- `spi-adapter-module-placement.md` (PP-20260529-spi-adapter-placement)
