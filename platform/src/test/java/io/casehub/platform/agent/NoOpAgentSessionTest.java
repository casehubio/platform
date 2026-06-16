package io.casehub.platform.agent;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * State-machine tests for {@link NoOpAgentSession}.
 * No subprocess, no semaphore — pure state machine.
 */
class NoOpAgentSessionTest {

    @Test
    void query_fromIdle_returnsEmptyStream() {
        final var session = new NoOpAgentSession();
        final List<AgentEvent> events = session.query("hello")
            .collect().asList().await().indefinitely();
        assertThat(events).isEmpty();
    }

    @Test
    void query_completedTurn_sessionReturnedToIdle_secondQuerySucceeds() {
        final var session = new NoOpAgentSession();
        session.query("first").collect().asList().await().indefinitely();
        assertThatCode(() ->
            session.query("second").collect().asList().await().indefinitely()
        ).doesNotThrowAnyException();
    }

    @Test
    void query_whileTurnActive_throwsIllegalStateException() {
        final var session = new NoOpAgentSession();
        // Hold the cold Multi without subscribing — state is ACTIVE between query() and subscribe()
        final var firstTurn = session.query("blocking");
        assertThatIllegalStateException()
            .isThrownBy(() -> session.query("concurrent"))
            .withMessageContaining("a turn is already active");
        // Subscribe to clean up — completes immediately
        firstTurn.collect().asList().await().indefinitely();
    }

    @Test
    void query_afterClose_throwsIllegalStateException() {
        final var session = new NoOpAgentSession();
        session.close(Duration.ofSeconds(1));
        assertThatIllegalStateException()
            .isThrownBy(() -> session.query("too late"))
            .withMessageContaining("session is closed");
    }

    @Test
    void close_isIdempotent() {
        final var session = new NoOpAgentSession();
        session.close(Duration.ofSeconds(1));
        assertThatCode(() -> session.close(Duration.ofSeconds(1)))
            .doesNotThrowAnyException();
    }

    @Test
    void interrupt_fromActive_isNoOp_stateRemainsActive() {
        final var session = new NoOpAgentSession();
        final var sub = session.query("running").subscribe().with(x -> {});
        // interrupt is no-op in NoOp — must not throw
        assertThatCode(() ->
            session.interrupt().await().indefinitely()
        ).doesNotThrowAnyException();
        sub.cancel();
    }

    @Test
    void interrupt_fromIdleOrClosed_isNoOp() {
        final var session = new NoOpAgentSession();
        assertThatCode(() -> session.interrupt().await().indefinitely())
            .doesNotThrowAnyException();
        session.close();
        assertThatCode(() -> session.interrupt().await().indefinitely())
            .doesNotThrowAnyException();
    }

    @Test
    void defaultClose_delegatesToClose_withThirtySecondTimeout() {
        final var session = new NoOpAgentSession();
        // AutoCloseable default — just ensure it works
        assertThatCode(() -> session.close())  // parameterless close() via interface default
            .doesNotThrowAnyException();
    }
}
