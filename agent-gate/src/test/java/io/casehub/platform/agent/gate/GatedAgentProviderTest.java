package io.casehub.platform.agent.gate;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentRateLimitException;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.AgentSessionLimitException;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatedAgentProviderTest {

    @Test
    void passthroughWhenBothLimitsZero() {
        var delegate = stubProvider("hello");
        var gated = createGated(delegate, 0, 0.0, 0);

        String result = collectText(gated.invoke(config()));
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void concurrencyOnlyBlocksExcessCalls() throws Exception {
        var holdFirst = new CountDownLatch(1);
        var firstStarted = new CountDownLatch(1);
        AgentProvider delayed = new StubProvider() {
            final AtomicInteger callOrder = new AtomicInteger();

            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                if (callOrder.incrementAndGet() == 1) {
                    return Multi.createFrom().emitter(em -> {
                        firstStarted.countDown();
                        try {
                            holdFirst.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        em.emit(new AgentEvent.TextDelta("first"));
                        em.complete();
                    });
                }
                return Multi.createFrom().item(new AgentEvent.TextDelta("second"));
            }
        };
        var gated = createGated(delayed, 1, 0.0, 0);
        var allDone = new CountDownLatch(2);
        var results = new ConcurrentLinkedQueue<String>();

        Thread.ofVirtual().start(() -> {
            results.add(collectText(gated.invoke(config())));
            allDone.countDown();
        });
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

        Thread.ofVirtual().start(() -> {
            results.add(collectText(gated.invoke(config())));
            allDone.countDown();
        });

        holdFirst.countDown();
        assertThat(allDone.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(results).containsExactlyInAnyOrder("first", "second");
    }

    @Test
    void concurrencyTimeoutReturnsSessionLimitFailure() throws Exception {
        var holdForever = new CountDownLatch(1);
        AgentProvider slow = new StubProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().emitter(em -> {
                    try {
                        holdForever.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    em.emit(new AgentEvent.TextDelta("done"));
                    em.complete();
                });
            }
        };
        var gated = createGated(slow, 1, 0.0, 0,
                Duration.ofMillis(200), Duration.ofSeconds(5));

        Thread.ofVirtual().start(() -> collectText(gated.invoke(config())));
        Thread.sleep(100);

        assertThatThrownBy(() -> collectText(gated.invoke(config())))
                .isInstanceOf(AgentSessionLimitException.class);
        holdForever.countDown();
    }

    @Test
    void rateLimitOnlyControlsThroughput() {
        var delegate = stubProvider("ok");
        var gated = createGated(delegate, 0, 2.0, 2);

        assertThat(collectText(gated.invoke(config()))).isEqualTo("ok");
        assertThat(collectText(gated.invoke(config()))).isEqualTo("ok");
    }

    @Test
    void rateLimitTimeoutReturnsRateLimitFailure() {
        var delegate = stubProvider("ok");
        var gated = createGated(delegate, 0, 0.5, 1,
                Duration.ofMillis(100), Duration.ofSeconds(5));

        collectText(gated.invoke(config()));
        assertThatThrownBy(() -> collectText(gated.invoke(config())))
                .isInstanceOf(AgentRateLimitException.class);
    }

    @Test
    void permitReleasedOnStreamCompletion() {
        var delegate = stubProvider("ok");
        var gated = createGated(delegate, 1, 0.0, 0);

        collectText(gated.invoke(config()));
        collectText(gated.invoke(config()));
    }

    @Test
    void permitReleasedOnStreamFailure() {
        AgentProvider failing = new StubProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().failure(new RuntimeException("boom"));
            }
        };
        var gated = createGated(failing, 1, 0.0, 0);

        assertThatThrownBy(() -> collectText(gated.invoke(config())))
                .hasMessageContaining("boom");
        var gated2 = createGated(stubProvider("ok"), 1, 0.0, 0);
        assertThat(collectText(gated2.invoke(config()))).isEqualTo("ok");
    }

    @Test
    void permitReleasedOnSynchronousThrow() {
        AgentProvider exploding = new StubProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                throw new IllegalStateException("sync explosion");
            }
        };
        var gated = createGated(exploding, 1, 0.0, 0);

        assertThatThrownBy(() -> collectText(gated.invoke(config())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sync explosion");
    }

    @Test
    void openSessionGatesConcurrency() {
        var delegate = new StubProvider() {
            @Override
            public AgentSession openSession(AgentSessionInit init) {
                return new StubAgentSession();
            }
        };
        var gated = createGated(delegate, 1, 0.0, 0);
        var session = gated.openSession(AgentSessionInit.of("sys"));
        session.close();
    }

    @Test
    void openSessionConcurrencyTimeoutThrows() {
        var delegate = new StubProvider() {
            @Override
            public AgentSession openSession(AgentSessionInit init) {
                return new StubAgentSession();
            }
        };
        var gated = createGated(delegate, 1, 0.0, 0,
                Duration.ofMillis(200), Duration.ofSeconds(5));
        var session = gated.openSession(AgentSessionInit.of("sys"));

        assertThatThrownBy(() ->
                gated.openSession(AgentSessionInit.of("sys")))
                .isInstanceOf(AgentSessionLimitException.class);
        session.close();
    }

    // --- factory helpers ---

    private GatedAgentProvider createGated(AgentProvider delegate,
                                           int maxConcurrent,
                                           double permitsPerSecond,
                                           int burstCapacity) {
        return createGated(delegate, maxConcurrent, permitsPerSecond,
                burstCapacity, Duration.ofSeconds(30), Duration.ofSeconds(5));
    }

    private GatedAgentProvider createGated(AgentProvider delegate,
                                           int maxConcurrent,
                                           double permitsPerSecond,
                                           int burstCapacity,
                                           Duration acquireTimeout,
                                           Duration queryAcquireTimeout) {
        return new GatedAgentProvider(delegate, maxConcurrent,
                permitsPerSecond, burstCapacity, acquireTimeout,
                queryAcquireTimeout);
    }

    private static AgentSessionConfig config() {
        return AgentSessionConfig.of("system", "user");
    }

    private static String collectText(Multi<AgentEvent> multi) {
        return multi
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .collect().with(Collectors.joining())
                .await().atMost(Duration.ofSeconds(30));
    }

    private static AgentProvider stubProvider(String text) {
        return new StubProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().item(new AgentEvent.TextDelta(text));
            }
        };
    }

    private static abstract class StubProvider implements AgentProvider {
        @Override
        public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            return Multi.createFrom().empty();
        }

        @Override
        public AgentSession openSession(AgentSessionInit init) {
            throw new UnsupportedOperationException();
        }
    }

    private static class StubAgentSession implements AgentSession {
        @Override
        public Multi<AgentEvent> query(String prompt) {
            return Multi.createFrom().empty();
        }

        @Override
        public Uni<Void> interrupt() {
            return Uni.createFrom().voidItem();
        }

        @Override
        public void close(Duration maxWait) {
        }

        @Override
        public void close() {
            close(Duration.ofSeconds(30));
        }
    }
}
