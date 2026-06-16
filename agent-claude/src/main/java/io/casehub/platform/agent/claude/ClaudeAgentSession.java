package io.casehub.platform.agent.claude;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProcessException;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentTimeoutException;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import org.jboss.logging.Logger;
import org.springaicommunity.claude.agent.sdk.ClaudeAsyncClient;
import reactor.adapter.JdkFlowAdapter;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Multi-turn {@link AgentSession} backed by {@link ClaudeAsyncClient}.
 *
 * <p>Package-private — callers interact through {@link AgentSession} only.
 *
 * <p>Two construction paths:
 * <ul>
 *   <li>Production: takes a real {@link ClaudeAsyncClient}, null {@code turnFactory}
 *   <li>Test: takes a {@code Function<String, Multi<AgentEvent>>} factory, null {@code sdkClient}
 * </ul>
 *
 * <p>The session is serial. The state machine ({@link State}) is enforced via
 * {@link AtomicReference} CAS operations. See the spec for the full transition table.
 */
class ClaudeAgentSession implements AgentSession {

    private static final Logger LOG = Logger.getLogger(ClaudeAgentSession.class);

    private enum State { IDLE, ACTIVE, CLOSED }

    // Production path: non-null sdkClient, null turnFactory.
    // Test path: null sdkClient, non-null turnFactory.
    private final ClaudeAsyncClient sdkClient;

    // Non-null in tests only — set by test constructor, null in production.
    // When non-null, query() delegates to this factory instead of sdkClient.
    private final Function<String, Multi<AgentEvent>> turnFactory;

    private final Duration timeout;
    private final Semaphore semaphore;
    private final CopyOnWriteArraySet<ClaudeAsyncClient> activeSessions;
    private final ScheduledExecutorService timeoutScheduler;

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

    // Tracks whether sdkClient.connect() has been called yet.
    // false → first query() uses connect(); true → subsequent queries use query().
    private final AtomicBoolean sessionStarted = new AtomicBoolean(false);

    // Nullable; null disables all session-level correlation logging.
    private final String correlationId;

    // Incremented after each successful IDLE→ACTIVE CAS (not on failed concurrent calls).
    private final AtomicInteger turnCounter = new AtomicInteger(0);

    // Written to volatile field BEFORE the IDLE→ACTIVE CAS; read by close() for drain wait.
    private volatile CompletableFuture<Void> currentTurnFuture;

    private volatile ScheduledFuture<?> currentTimeoutFuture;

    /** Production constructor. */
    ClaudeAgentSession(final ClaudeAsyncClient sdkClient,
                       final Duration timeout,
                       final Semaphore semaphore,
                       final CopyOnWriteArraySet<ClaudeAsyncClient> activeSessions,
                       final ScheduledExecutorService timeoutScheduler,
                       final String correlationId) {
        this.sdkClient = sdkClient;
        this.turnFactory = null;
        this.timeout = timeout;
        this.semaphore = semaphore;
        this.activeSessions = activeSessions;
        this.timeoutScheduler = timeoutScheduler;
        this.correlationId = correlationId;
    }

    /** Test constructor — bypasses SDK; sdkClient is null. */
    ClaudeAgentSession(final ClaudeAgentProperties properties,
                       final Function<String, Multi<AgentEvent>> turnFactory,
                       final Semaphore semaphore,
                       final CopyOnWriteArraySet<ClaudeAsyncClient> activeSessions,
                       final ScheduledExecutorService timeoutScheduler) {
        this.sdkClient = null;
        this.turnFactory = turnFactory;
        this.timeout = properties.defaultTimeout();
        this.semaphore = semaphore;
        this.activeSessions = activeSessions;
        this.timeoutScheduler = timeoutScheduler;
        this.correlationId = null;
    }

    @Override
    public Multi<AgentEvent> query(final String prompt) {
        // Step 1 — write future to volatile field BEFORE CAS (JMM visibility guarantee).
        final var pendingFuture = new CompletableFuture<Void>();
        this.currentTurnFuture = pendingFuture;

        // Step 2 — CAS IDLE → ACTIVE.
        if (!state.compareAndSet(State.IDLE, State.ACTIVE)) {
            final State current = state.get();
            throw new IllegalStateException(current == State.CLOSED
                ? "session is closed"
                : "a turn is already active — wait for it to complete or call interrupt()");
        }

        final int turn = turnCounter.incrementAndGet();
        logTurn("started", turn);

        // Step 3 — per-turn timeout flag.
        final var timedOut = new AtomicBoolean(false);

        // Step 4 — schedule wall-clock timeout.
        final var timeoutFuture = timeoutScheduler.schedule(() -> {
            if (timedOut.compareAndSet(false, true)) {
                if (sdkClient != null) sdkClient.close().subscribe();
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
        this.currentTimeoutFuture = timeoutFuture;

        // Step 5 — obtain turn stream.
        final Multi<AgentEvent> rawStream = buildTurnStream(prompt);

        // Steps 7→8→9→10: converter + transform + handlers + subscription shift.
        return rawStream
            // Step 7 — convert timeout-triggered completion to AgentTimeoutException.
            .onCompletion().call(() -> {
                if (timedOut.get()) {
                    return Uni.createFrom().failure(new AgentTimeoutException(timeout));
                }
                return Uni.createFrom().voidItem();
            })
            // Step 8 — type exceptions (before handlers so handlers receive typed exceptions).
            .onFailure().transform(e -> {
                if (e instanceof AgentTimeoutException) return e;
                if (timedOut.get()) return new AgentTimeoutException(timeout);
                return new AgentProcessException(
                    Objects.toString(e.getMessage(), e.getClass().getSimpleName()), e);
            })
            // Step 9 — termination handlers.
            .onCompletion().invoke(() -> {
                cancelTimeout(timeoutFuture);
                pendingFuture.complete(null);
                logTurn("completed", turn);
                state.compareAndSet(State.ACTIVE, State.IDLE);
            })
            .onFailure().invoke(e -> {
                cancelTimeout(timeoutFuture);
                pendingFuture.complete(null);
                logTurn("failed", turn);
                if (state.compareAndSet(State.ACTIVE, State.CLOSED)) {
                    closeSubprocess();
                    semaphore.release();
                }
            })
            .onCancellation().invoke(() -> {
                cancelTimeout(timeoutFuture);
                pendingFuture.complete(null);
                logTurn("cancelled", turn);
                if (state.compareAndSet(State.ACTIVE, State.CLOSED)) {
                    closeSubprocess();
                    semaphore.release();
                }
            })
            // Step 10 — shift subscription to worker pool (blocks on connect/query).
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Void> interrupt() {
        if (state.get() != State.ACTIVE) {
            return Uni.createFrom().voidItem();
        }
        if (sdkClient == null) {
            return Uni.createFrom().voidItem();  // test mode: no subprocess
        }
        return Uni.createFrom().publisher(
            JdkFlowAdapter.publisherToFlowPublisher(sdkClient.interrupt().flux())
        ).onFailure().recoverWithNull();
    }

    @Override
    public void close(final Duration maxWait) {
        final State prev = state.getAndSet(State.CLOSED);
        if (prev == State.CLOSED) {
            return;  // idempotent
        }

        if (prev == State.ACTIVE) {
            final CompletableFuture<Void> turnFuture = currentTurnFuture;
            if (turnFuture != null) {
                try {
                    turnFuture.get(maxWait.toMillis(), TimeUnit.MILLISECONDS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();  // restore interrupt status
                } catch (TimeoutException | ExecutionException | CancellationException ignored) {
                    // waited as long as we could — proceeding to force close
                }
            }
        }

        cancelTimeout(currentTimeoutFuture);
        if (sdkClient != null) activeSessions.remove(sdkClient);
        if (sdkClient != null) sdkClient.close().subscribe();
        // Note: semaphore released before subprocess teardown completes (async, up to 5s)
        if (correlationId != null) {
            LOG.infof("Agent session closed [correlationId=%s]", correlationId);
        }
        semaphore.release();
    }

    // ── internal helpers ─────────────────────────────────────────────────────

    /** Build the raw event stream for one turn — test path or production path. */
    private Multi<AgentEvent> buildTurnStream(final String prompt) {
        if (turnFactory != null) {
            return turnFactory.apply(prompt);
        }
        // Production: first turn uses connect(), subsequent use query().
        final var textFlux = sessionStarted.compareAndSet(false, true)
            ? sdkClient.connect(prompt).textStream()
            : sdkClient.query(prompt).textStream();

        return Multi.createFrom()
            .publisher(JdkFlowAdapter.publisherToFlowPublisher(textFlux))
            .map(text -> (AgentEvent) new AgentEvent.TextDelta(text));
    }

    private void closeSubprocess() {
        if (sdkClient != null) {
            activeSessions.remove(sdkClient);
            sdkClient.close().subscribe();
        }
    }

    private static void cancelTimeout(final ScheduledFuture<?> future) {
        if (future != null) future.cancel(false);
    }

    private void logTurn(final String event, final int turn) {
        if (correlationId != null) {
            LOG.infof("Agent session turn %s [correlationId=%s, turn=%d]",
                event, correlationId, turn);
        }
    }
}
