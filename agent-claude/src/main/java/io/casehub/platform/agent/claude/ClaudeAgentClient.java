package io.casehub.platform.agent.claude;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentMcpServer;
import io.casehub.platform.agent.AgentProcessException;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionLimitException;
import io.casehub.platform.agent.AgentTimeoutException;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.runtime.Startup;
import org.jboss.logging.Logger;
import org.springaicommunity.claude.agent.sdk.ClaudeAsyncClient;
import org.springaicommunity.claude.agent.sdk.ClaudeClient;
import org.springaicommunity.claude.agent.sdk.mcp.McpServerConfig;
import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Manages Claude CLI subprocess sessions behind a concurrency-limited semaphore.
 *
 * <p>{@code @Startup} forces eager initialization on the main thread during application
 * startup, before any reactive handlers can run. Without {@code @Startup}, ARC initializes
 * lazily on first injection. If that happens on the Vert.x IO thread,
 * {@link #validateBinary()} would call {@code ProcessBuilder.start().waitFor()} there —
 * blocking the IO thread.
 *
 * <p>Same pattern as {@code PathParserConfigurator} and {@code MongoPreferenceIndexes}
 * in this codebase.
 */
@Startup
@ApplicationScoped
public class ClaudeAgentClient {

    private static final Logger LOG = Logger.getLogger(ClaudeAgentClient.class);

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
     * and a test subclass risks firing {@code @PostConstruct} if CDI manages the instance.
     * Follows the {@code ScimActorDIDProvider} pattern established in this codebase.
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

    /** For testing only — allows tests to observe semaphore state without exposing the field. */
    int availablePermits() {
        return semaphore.availablePermits();
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
                    "claude binary at '" + binary + "' exited with code " + exitCode
                    + " — possible broken installation");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Interrupted while probing claude binary: " + binary, e);
        } catch (IOException e) {
            throw new IllegalStateException(
                "claude binary not found or not executable: " + binary
                + " — configure casehub.platform.agent.claude.binary-path "
                + "or ensure 'claude' is on PATH", e);
        }
        LOG.infof("claude binary resolved at '%s'. Authentication not verified — "
                  + "AgentProcessException will surface on first invocation if unauthenticated.",
                  binary);
    }

    /**
     * Stream {@link AgentEvent.TextDelta} events until the agent completes or the
     * wall-clock timeout fires. All error cases are surfaced via {@code onFailure()} on the
     * returned Multi — {@code run()} never throws.
     *
     * <p>Responsibility split:
     * <ul>
     *   <li>{@code buildEventStream()} owns: timer cancel, session deregister, sdkClient.close()
     *   <li>{@code run()} owns: semaphore release — on all paths, as the outer handler layer
     * </ul>
     *
     * <p>Threading: subscription shifted to the worker pool via {@code runSubscriptionOn()}.
     * The SDK creates a subprocess during subscription setup — this blocks. Without the shift,
     * subscription blocks the Vert.x IO thread.
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

    /**
     * Build the event stream. If {@code streamFactory} is non-null (test path), delegates
     * to it. Otherwise creates a {@link ClaudeAsyncClient} per call, schedules a wall-clock
     * timeout, and bridges {@code Flux<String>} to {@code Multi<AgentEvent>}.
     */
    /* package-private */ Multi<AgentEvent> buildEventStream(AgentSessionConfig config) {
        if (streamFactory != null) {
            return streamFactory.apply(config);
        }

        // Production path
        Duration effectiveTimeout = config.timeout() != null
            ? config.timeout()
            : properties.defaultTimeout();

        // Build SDK client per invocation
        ClaudeClient.AsyncSpec builder = ClaudeClient.async()
            .workingDirectory(Path.of(System.getProperty("user.dir")))
            .systemPrompt(config.systemPrompt());

        // Set binary path if configured
        properties.binaryPath().ifPresent(builder::claudePath);

        // Convert and set MCP servers
        Map<String, McpServerConfig> sdkMcpServers = toSdkMcpServers(config.mcpServers());
        if (!sdkMcpServers.isEmpty()) {
            builder.mcpServers(sdkMcpServers);
        }

        ClaudeAsyncClient sdkClient = builder.build();
        activeSessions.add(sdkClient);

        AtomicBoolean timedOut = new AtomicBoolean(false);

        // Schedule wall-clock timeout: close the subprocess if it exceeds the limit
        ScheduledFuture<?> timeoutFuture = timeoutScheduler.schedule(() -> {
            if (timedOut.compareAndSet(false, true)) {
                sdkClient.close().subscribe();
            }
        }, effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);

        try {
            // Obtain Flux<String> via textStream() — token-level text deltas
            Flux<String> textFlux = sdkClient.connect(config.userPrompt()).textStream();

            // Bridge Flux<String> → Flow.Publisher<String> → Multi<AgentEvent>
            Multi<AgentEvent> eventStream = Multi.createFrom()
                .publisher(JdkFlowAdapter.publisherToFlowPublisher(textFlux))
                .map(text -> (AgentEvent) new AgentEvent.TextDelta(text))
                .onFailure().transform(e ->
                    timedOut.get()
                        ? new AgentTimeoutException(effectiveTimeout)
                        : new AgentProcessException(
                            Objects.toString(e.getMessage(), e.getClass().getSimpleName()), e))
                .onCompletion().invoke(() -> {
                    logCorrelationEvent(config, "completed");
                    cleanup(timeoutFuture, sdkClient);
                })
                .onFailure().invoke(t -> {
                    logCorrelationEvent(config, "failed");
                    cleanup(timeoutFuture, sdkClient);
                })
                .onCancellation().invoke(() -> {
                    logCorrelationEvent(config, "cancelled");
                    cleanup(timeoutFuture, sdkClient);
                });

            if (config.correlationId() != null) {
                LOG.infof("Agent session started [correlationId=%s]", config.correlationId());
            }

            return eventStream;
        } catch (Exception e) {
            // buildEventStream() threw synchronously after registering sdkClient and timer.
            // Clean up timer and session before rethrowing.
            timeoutFuture.cancel(false);
            activeSessions.remove(sdkClient);
            try {
                sdkClient.close().subscribe();
            } catch (Exception ignored) {
                // isolation: don't mask the original exception
            }
            throw e;
        }
    }

    private void logCorrelationEvent(AgentSessionConfig config, String event) {
        if (config.correlationId() != null) {
            LOG.infof("agent session %s correlationId=%s", event, config.correlationId());
        }
    }

    /**
     * Cancel timeout, deregister session, close SDK client. Called on all three termination
     * paths (completion, failure, cancellation). {@code sdkClient.close()} is idempotent —
     * safe even if already closed by the timeout timer.
     */
    private void cleanup(ScheduledFuture<?> timeoutFuture, ClaudeAsyncClient sdkClient) {
        timeoutFuture.cancel(false);
        activeSessions.remove(sdkClient);
        try {
            sdkClient.close().subscribe();
        } catch (Exception ignored) {
            // isolation: one failed close must not propagate
        }
    }

    /**
     * Convert platform {@link AgentMcpServer} list to SDK {@link McpServerConfig} map.
     * Uses index-based keys: "stdio-0", "sse-0", "http-0" etc.
     */
    private static Map<String, McpServerConfig> toSdkMcpServers(
            List<AgentMcpServer> mcpServers) {
        if (mcpServers == null || mcpServers.isEmpty()) {
            return Map.of();
        }
        Map<String, McpServerConfig> result = new HashMap<>();
        int stdioIdx = 0, sseIdx = 0, httpIdx = 0;
        for (AgentMcpServer server : mcpServers) {
            switch (server) {
                case AgentMcpServer.Stdio s ->
                    result.put("stdio-" + stdioIdx++,
                        new McpServerConfig.McpStdioServerConfig(
                            s.command(), s.args(), s.env()));
                case AgentMcpServer.Sse s ->
                    result.put("sse-" + sseIdx++,
                        new McpServerConfig.McpSseServerConfig(s.url(), s.headers()));
                case AgentMcpServer.Http s ->
                    result.put("http-" + httpIdx++,
                        new McpServerConfig.McpHttpServerConfig(s.url(), s.headers()));
            }
        }
        return result;
    }

    @PreDestroy
    void shutdown() {
        // Shut down timer scheduler first — no new timeouts will fire.
        // Then close all sessions that are still active.
        // Isolate per-client so one failure doesn't abort the rest.
        timeoutScheduler.shutdownNow();
        activeSessions.forEach(c -> {
            try {
                c.close().subscribe();
            } catch (Exception ignored) {
                // isolation
            }
        });
    }
}
