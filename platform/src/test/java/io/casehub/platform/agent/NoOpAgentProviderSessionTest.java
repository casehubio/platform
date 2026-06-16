package io.casehub.platform.agent;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@QuarkusTest
class NoOpAgentProviderSessionTest {

    @Inject
    AgentProvider provider;

    @Test
    void openSession_returnsWorkingNoOpSession() {
        try (final var session = provider.openSession(AgentSessionInit.of("sys"))) {
            final var events = session.query("hello")
                .collect().asList().await().indefinitely();
            assertThat(events).isEmpty();
        }
    }

    @Test
    void openSession_sessionClosedAfterTryWithResources_queryThrows() {
        final AgentSession session = provider.openSession(AgentSessionInit.of("sys"));
        session.close(Duration.ofSeconds(1));
        assertThatIllegalStateException()
            .isThrownBy(() -> session.query("too late"))
            .withMessageContaining("session is closed");
    }
}
