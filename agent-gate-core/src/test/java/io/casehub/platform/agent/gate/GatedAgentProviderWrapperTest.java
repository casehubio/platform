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

class GatedAgentProviderWrapperTest {

    @Test
    void passthroughWhenAllLimitsZero() {
        var delegate = stubProvider("hello");
        var wrapper = createWrapper(delegate, 0, 0.0, 0);

        String result = collectText(wrapper.invoke(config()));
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void concurrencyBlocksExcessCalls() throws Exception {
        var holdFirst = new CountDownLatch(1);
        var firstStarted = new CountDownLatch(1);
        AgentProvider delayed = new StubProvider() {
            final AtomicInteger callOrder = new AtomicInteger();

            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                if (callOrder.incrementAndGet() == 1) {
                    return Multi.createFrom().emitter(em -> {
                        firstStarted.countDown();
                        try { holdFirst.await(); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        em.emit(new AgentEvent.TextDelta("first"));
                        em.complete();
                    });
                }
                return Multi.createFrom().item(new AgentEvent.TextDelta("second"));
            }
        };
        var wrapper = createWrapper(delayed, 1, 0.0, 0);
        var allDone = new CountDownLatch(2);
        var results = new ConcurrentLinkedQueue<String>();

        Thread.ofVirtual().start(() -> {
            results.add(collectText(wrapper.invoke(config())));
            allDone.countDown();
        });
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

        Thread.ofVirtual().start(() -> {
            results.add(collectText(wrapper.invoke(config())));
            allDone.countDown();
        });

        holdFirst.countDown();
        assertThat(allDone.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(results).containsExactlyInAnyOrder("first", "second");
    }

    @Test
    void concurrencyTimeoutThrowsSessionLimitException() throws Exception {
        var holdForever = new CountDownLatch(1);
        AgentProvider slow = new StubProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().emitter(em -> {
                    try { holdForever.await(); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    em.emit(new AgentEvent.TextDelta("done"));
                    em.complete();
                });
            }
        };
        var wrapper = createWrapper(slow, 1, 0.0, 0,
                Duration.ofMillis(200), Duration.ofSeconds(5));

        Thread.ofVirtual().start(() -> collectText(wrapper.invoke(config())));
        Thread.sleep(100);

        assertThatThrownBy(() -> collectText(wrapper.invoke(config())))
                .isInstanceOf(AgentSessionLimitException.class);
        holdForever.countDown();
    }

    @Test
    void rateLimitControlsThroughput() {
        var delegate = stubProvider("ok");
        var wrapper = createWrapper(delegate, 0, 2.0, 2);

        assertThat(collectText(wrapper.invoke(config()))).isEqualTo("ok");
        assertThat(collectText(wrapper.invoke(config()))).isEqualTo("ok");
    }

    @Test
    void rateLimitTimeoutThrowsRateLimitException() {
        var delegate = stubProvider("ok");
        var wrapper = createWrapper(delegate, 0, 0.5, 1,
                Duration.ofMillis(100), Duration.ofSeconds(5));

        collectText(wrapper.invoke(config()));
        assertThatThrownBy(() -> collectText(wrapper.invoke(config())))
                .isInstanceOf(AgentRateLimitException.class);
    }

    @Test
    void permitReleasedOnStreamCompletion() {
        var delegate = stubProvider("ok");
        var wrapper = createWrapper(delegate, 1, 0.0, 0);

        collectText(wrapper.invoke(config()));
        collectText(wrapper.invoke(config()));
    }

    @Test
    void permitReleasedOnStreamFailure() {
        AgentProvider failing = new StubProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().failure(new RuntimeException("boom"));
            }
        };
        var wrapper = createWrapper(failing, 1, 0.0, 0);

        assertThatThrownBy(() -> collectText(wrapper.invoke(config())))
                .hasMessageContaining("boom");
    }

    @Test
    void permitReleasedOnSynchronousThrow() {
        AgentProvider exploding = new StubProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                throw new IllegalStateException("sync explosion");
            }
        };
        var wrapper = createWrapper(exploding, 1, 0.0, 0);

        assertThatThrownBy(() -> collectText(wrapper.invoke(config())))
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
        var wrapper = createWrapper(delegate, 1, 0.0, 0);
        var session = wrapper.openSession(AgentSessionInit.of("sys"));
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
        var wrapper = createWrapper(delegate, 1, 0.0, 0,
                Duration.ofMillis(200), Duration.ofSeconds(5));
        var session = wrapper.openSession(AgentSessionInit.of("sys"));

        assertThatThrownBy(() ->
                wrapper.openSession(AgentSessionInit.of("sys")))
                .isInstanceOf(AgentSessionLimitException.class);
        session.close();
    }

    // --- factory helpers ---

    private GatedAgentProviderWrapper createWrapper(AgentProvider delegate,
                                                     int maxConcurrent,
                                                     double permitsPerSecond,
                                                     int burstCapacity) {
        return createWrapper(delegate, maxConcurrent, permitsPerSecond,
                burstCapacity, Duration.ofSeconds(30), Duration.ofSeconds(5));
    }

    private GatedAgentProviderWrapper createWrapper(AgentProvider delegate,
                                                     int maxConcurrent,
                                                     double permitsPerSecond,
                                                     int burstCapacity,
                                                     Duration acquireTimeout,
                                                     Duration queryAcquireTimeout) {
        var props = new StubAgentGateProperties(acquireTimeout, queryAcquireTimeout,
                maxConcurrent, permitsPerSecond, burstCapacity);
        return new GatedAgentProviderWrapper(delegate, props, new SessionRegistry());
    }

    // --- stubs ---

    private record StubAgentGateProperties(
            Duration acquireTimeout, Duration queryAcquireTimeout,
            int maxConcurrent, double permitsPerSec, int burst
    ) implements AgentGateProperties {
        @Override public Concurrency concurrency() {
            return () -> maxConcurrent;
        }
        @Override public TokenBucketConfig tokenBucket() {
            return new TokenBucketConfig() {
                @Override public double permitsPerSecond() { return permitsPerSec; }
                @Override public int burstCapacity() { return burst; }
            };
        }
        @Override public SlidingWindow slidingWindow() {
            return new SlidingWindow() {
                @Override public int maxActions() { return 0; }
                @Override public int windowSeconds() { return 60; }
            };
        }
        @Override public Reaper reaper() {
            return new Reaper() {
                @Override public Duration scanInterval() { return Duration.ofSeconds(60); }
                @Override public Duration warnThreshold() { return Duration.ofMinutes(5); }
                @Override public boolean forceCloseEnabled() { return false; }
                @Override public Duration forceCloseThreshold() { return Duration.ofMinutes(30); }
                @Override public Duration maxRegistryAge() { return Duration.ofHours(24); }
            };
        }
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
        @Override public Multi<AgentEvent> query(String prompt) {
            return Multi.createFrom().empty();
        }
        @Override public Uni<Void> interrupt() {
            return Uni.createFrom().voidItem();
        }
        @Override public void close(Duration maxWait) {}
        @Override public void close() { close(Duration.ofSeconds(30)); }
    }
}
