package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationScopeTest {

    @Test
    void expectThenSend_awaitReturnsValue() throws Exception {
        var ch = new DefaultOrcChannel<String>("test");
        var cs = new CorrelationScope<>(ch, v -> v.split(":")[0]);

        cs.expectResponse("order-1", Duration.ofSeconds(5));

        Thread.ofVirtual().start(() -> {
            try { ch.send("order-1:payload"); } catch (InterruptedException e) {}
        });

        String result = cs.awaitResponse("order-1");
        assertThat(result).isEqualTo("order-1:payload");
        assertThat(cs.pendingCount()).isZero();

        cs.close();
    }

    @Test
    void multipleKeys_routedCorrectly() throws Exception {
        var ch = new DefaultOrcChannel<String>("test");
        var cs = new CorrelationScope<>(ch, v -> v.split(":")[0]);

        cs.expectResponse("a", Duration.ofSeconds(5));
        cs.expectResponse("b", Duration.ofSeconds(5));

        Thread.ofVirtual().start(() -> {
            try {
                ch.send("b:second");
                ch.send("a:first");
            } catch (InterruptedException e) {}
        });

        assertThat(cs.awaitResponse("a")).isEqualTo("a:first");
        assertThat(cs.awaitResponse("b")).isEqualTo("b:second");
        cs.close();
    }

    @Test
    void duplicateKey_throwsIllegalState() {
        var ch = new DefaultOrcChannel<String>("test");
        var cs = new CorrelationScope<>(ch, v -> v);
        cs.expectResponse("key", Duration.ofSeconds(5));

        assertThatThrownBy(() -> cs.expectResponse("key", Duration.ofSeconds(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already pending");

        cs.close();
    }

    @Test
    void timeout_throwsCorrelationTimeoutException() {
        var ch = new DefaultOrcChannel<String>("test");
        var cs = new CorrelationScope<>(ch, v -> v);
        cs.expectResponse("key", Duration.ofMillis(50));

        assertThatThrownBy(() -> cs.awaitResponse("key"))
                .isInstanceOf(CorrelationTimeoutException.class)
                .isInstanceOf(java.util.concurrent.TimeoutException.class);

        cs.close();
    }

    @Test
    void close_cancelsAllPending() throws Exception {
        var ch = new DefaultOrcChannel<String>("test");
        var cs = new CorrelationScope<>(ch, v -> v);
        cs.expectResponse("key", Duration.ofSeconds(30));

        var latch  = new java.util.concurrent.CountDownLatch(1);
        var caught = new java.util.concurrent.atomic.AtomicReference<Exception>();
        Thread.ofVirtual().start(() -> {
            try {
                cs.awaitResponse("key");
            } catch (Exception e) {
                caught.set(e);
            }
            latch.countDown();
        });

        Thread.sleep(50);
        cs.close();
        assertThat(latch.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(caught.get()).isInstanceOf(InterruptedException.class);
    }

    @Test
    void pendingCount_tracksRegistrations() {
        var ch = new DefaultOrcChannel<String>("test");
        var cs = new CorrelationScope<>(ch, v -> v);

        assertThat(cs.pendingCount()).isZero();
        cs.expectResponse("a", Duration.ofSeconds(5));
        assertThat(cs.pendingCount()).isEqualTo(1);
        cs.expectResponse("b", Duration.ofSeconds(5));
        assertThat(cs.pendingCount()).isEqualTo(2);

        cs.close();
    }

    @Test
    void oldestPendingAge_returnsEmpty_whenNoPending() {
        var ch = new DefaultOrcChannel<String>("test");
        var cs = new CorrelationScope<>(ch, v -> v);
        assertThat(cs.oldestPendingAge()).isEmpty();
        cs.close();
    }

    @Test
    void oldestPendingAge_returnsDuration_whenPending() throws Exception {
        var ch = new DefaultOrcChannel<String>("test");
        var cs = new CorrelationScope<>(ch, v -> v);
        cs.expectResponse("key", Duration.ofSeconds(30));
        Thread.sleep(20);
        assertThat(cs.oldestPendingAge()).isPresent();
        assertThat(cs.oldestPendingAge().get().toMillis()).isGreaterThanOrEqualTo(15);
        cs.close();
    }

    @Test
    void hasPending_tracksState() throws Exception {
        var ch = new DefaultOrcChannel<String>("test");
        var cs = new CorrelationScope<>(ch, v -> v);

        assertThat(cs.hasPending("key")).isFalse();
        cs.expectResponse("key", Duration.ofSeconds(5));
        assertThat(cs.hasPending("key")).isTrue();

        ch.send("key");
        cs.awaitResponse("key");
        assertThat(cs.hasPending("key")).isFalse();

        cs.close();
    }

    @Test
    void close_idempotent() {
        var ch = new DefaultOrcChannel<String>("test");
        var cs = new CorrelationScope<>(ch, v -> v);
        cs.close();
        cs.close();
    }

    @Test
    void expectResponse_closedScope_throwsIllegalState() {
        var ch = new DefaultOrcChannel<String>("test");
        var cs = new CorrelationScope<>(ch, v -> v);
        cs.close();

        assertThatThrownBy(() -> cs.expectResponse("key", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void channelErrorClose_failsAllPendingWithCause() throws Exception {
        var ch = new DefaultOrcChannel<String>("test");
        var cs = new CorrelationScope<>(ch, v -> v);
        cs.expectResponse("key", Duration.ofSeconds(30));

        var latch  = new java.util.concurrent.CountDownLatch(1);
        var caught = new java.util.concurrent.atomic.AtomicReference<Exception>();
        Thread.ofVirtual().start(() -> {
            try {
                cs.awaitResponse("key");
            } catch (Exception e) {
                caught.set(e);
            }
            latch.countDown();
        });

        Thread.sleep(50);
        ch.close(new RuntimeException("upstream failure"));
        assertThat(latch.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(caught.get()).isInstanceOf(RuntimeException.class)
                                .hasMessage("upstream failure");

        cs.close();
    }

    @Test
    void extractorThrows_messageDropped_listenerContinues() throws Exception {
        var callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        var ch        = new DefaultOrcChannel<String>("test");
        var cs = new CorrelationScope<>(ch, v -> {
            int c = callCount.incrementAndGet();
            if (c == 1) {throw new RuntimeException("bad message");}
            return v.split(":")[0];
        });

        cs.expectResponse("good", Duration.ofSeconds(5));
        ch.send("bad-message");
        ch.send("good:payload");

        assertThat(cs.awaitResponse("good")).isEqualTo("good:payload");
        cs.close();
    }

    @Test
    void extractorReturnsNull_messageDropped_listenerContinues() throws Exception {
        var ch = new DefaultOrcChannel<String>("test");
        var cs = new CorrelationScope<>(ch, v -> v.startsWith("null") ? null : v);

        cs.expectResponse("valid", Duration.ofSeconds(5));
        ch.send("null-value");
        ch.send("valid");

        assertThat(cs.awaitResponse("valid")).isEqualTo("valid");
        cs.close();
    }

    @Test
    void speedMultiplier_affectsTimeout() {
        var                                          ch   = new DefaultOrcChannel<String>("test");
        io.casehub.yaml.core.runtime.SpeedMultiplier fast = () -> 10.0;
        var                                          cs   = new CorrelationScope<>(ch, v -> v, fast);

        long start = System.nanoTime();
        cs.expectResponse("key", Duration.ofSeconds(1));

        assertThatThrownBy(() -> cs.awaitResponse("key"))
                .isInstanceOf(CorrelationTimeoutException.class);

        long elapsed = System.nanoTime() - start;
        assertThat(Duration.ofNanos(elapsed).toMillis()).isLessThan(500);

        cs.close();
    }

    @Test
    void earlyArrival_completesImmediately() throws Exception {
        var ch = new DefaultOrcChannel<String>("test");
        var cs = new CorrelationScope<>(ch, v -> v.split(":")[0]);

        ch.send("key:early-payload");
        Thread.sleep(50);

        cs.expectResponse("key", Duration.ofSeconds(5));
        String result = cs.awaitResponse("key");
        assertThat(result).isEqualTo("key:early-payload");

        cs.close();
    }

    @Test
    void forScope_registersAsPrimitive() {
        var scope = new DefaultExecutionScope();
        var cs    = CorrelationScope.<String, String>forScope(scope, "test-corr", v -> v);

        assertThat(scope.primitive("test-corr", CorrelationScope.class)).isSameAs(cs);

        cs.close();
        scope.close();
    }

    @Test
    void forScope_scopeClose_cascadesToCorrelationScope() throws Exception {
        var scope = new DefaultExecutionScope();
        var cs    = CorrelationScope.<String, String>forScope(scope, "test-corr", v -> v);

        cs.expectResponse("key", Duration.ofSeconds(30));

        var latch  = new java.util.concurrent.CountDownLatch(1);
        var caught = new java.util.concurrent.atomic.AtomicReference<Exception>();
        Thread.ofVirtual().start(() -> {
            try {
                cs.awaitResponse("key");
            } catch (Exception e) {
                caught.set(e);
            }
            latch.countDown();
        });

        Thread.sleep(50);
        scope.close();
        assertThat(latch.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(caught.get()).isInstanceOf(InterruptedException.class);
    }
}
