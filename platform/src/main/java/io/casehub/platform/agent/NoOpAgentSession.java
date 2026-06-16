package io.casehub.platform.agent;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * No-op {@link AgentSession} returned by {@link NoOpAgentProvider#openSession}.
 * Enforces the state machine with no subprocess and no semaphore.
 * Package-private — callers interact through {@link AgentSession} only.
 */
class NoOpAgentSession implements AgentSession {

    private enum State { IDLE, ACTIVE, CLOSED }

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

    @Override
    public Multi<AgentEvent> query(final String prompt) {
        if (!state.compareAndSet(State.IDLE, State.ACTIVE)) {
            final State current = state.get();
            throw new IllegalStateException(current == State.CLOSED
                ? "session is closed"
                : "a turn is already active — wait for it to complete or call interrupt()");
        }
        return Multi.createFrom().<AgentEvent>empty()
            .onCompletion().invoke(() -> state.compareAndSet(State.ACTIVE, State.IDLE));
    }

    @Override
    public Uni<Void> interrupt() {
        return Uni.createFrom().voidItem();  // no subprocess to signal
    }

    @Override
    public void close(final Duration maxWait) {
        state.set(State.CLOSED);  // maxWait is ignored — no subprocess to drain
    }
}
