package io.casehub.platform.agent.gate;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.AgentSessionLimitException;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class GatedAgentProviderCdiTest {

    @Inject
    AgentProvider provider;

    @Test
    void decoratorIsActiveAndGatesConcurrency() throws Exception {
        var holdFirst = new CountDownLatch(1);
        var firstStarted = new CountDownLatch(1);
        var secondFailed = new AtomicBoolean(false);
        var allDone = new CountDownLatch(2);

        Thread.ofVirtual().start(() -> {
            try {
                provider.invoke(AgentSessionConfig.of("sys", "first"))
                        .onItem().invoke(e -> {
                            firstStarted.countDown();
                            try { holdFirst.await(); } catch (InterruptedException ignored) {}
                        })
                        .filter(e -> e instanceof AgentEvent.TextDelta)
                        .map(e -> ((AgentEvent.TextDelta) e).text())
                        .collect().with(Collectors.joining())
                        .await().atMost(Duration.ofSeconds(10));
            } catch (Exception ignored) {
            }
            allDone.countDown();
        });

        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

        Thread.ofVirtual().start(() -> {
            try {
                provider.invoke(AgentSessionConfig.of("sys", "second"))
                        .filter(e -> e instanceof AgentEvent.TextDelta)
                        .map(e -> ((AgentEvent.TextDelta) e).text())
                        .collect().with(Collectors.joining())
                        .await().atMost(Duration.ofSeconds(3));
            } catch (Exception e) {
                if (e.getCause() instanceof AgentSessionLimitException
                        || e instanceof AgentSessionLimitException) {
                    secondFailed.set(true);
                }
            }
            allDone.countDown();
        });

        assertThat(allDone.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(secondFailed.get())
                .describedAs("Second call should fail with AgentSessionLimitException")
                .isTrue();
        holdFirst.countDown();
    }

    @Alternative
    @Priority(1)
    @ApplicationScoped
    public static class TestAgentProvider implements AgentProvider {
        @Override
        public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            return Multi.createFrom().item(
                    new AgentEvent.TextDelta(config.userPrompt()));
        }

        @Override
        public AgentSession openSession(AgentSessionInit init) {
            throw new UnsupportedOperationException();
        }
    }
}
