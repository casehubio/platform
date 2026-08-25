package io.casehub.platform.governance;

import io.casehub.platform.api.governance.SessionIsolator;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class CdiSessionIsolatorTest {

    @Inject
    SessionIsolator isolator;

    @Test
    void isInjectable() {
        assertThat(isolator).isNotNull();
        assertThat(isolator).isInstanceOf(CdiSessionIsolator.class);
    }

    @Test
    void runIsolated_returnsResult() {
        String result = isolator.runIsolated(() -> "hello");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void runIsolated_runnable_executes() {
        AtomicReference<String> ref = new AtomicReference<>();
        isolator.runIsolated(() -> ref.set("executed"));
        assertThat(ref.get()).isEqualTo("executed");
    }

    @Test
    void runIsolated_getsDistinctRequestContext() {
        var outerState = Arc.container().requestContext().getState();
        var innerState = isolator.runIsolated(
                () -> Arc.container().requestContext().getState());
        assertThat(innerState).isNotSameAs(outerState);
    }

    @Test
    void runIsolated_restoresOriginalContext() {
        var before = Arc.container().requestContext().getState();
        isolator.runIsolated(() -> "work");
        var after = Arc.container().requestContext().getState();
        assertThat(after).isSameAs(before);
    }

    @Test
    void runIsolated_propagatesException() {
        try {
            isolator.runIsolated(() -> {
                throw new IllegalArgumentException("boom");
            });
            assertThat(true).as("should have thrown").isFalse();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("boom");
        }
        assertThat(Arc.container().requestContext().isActive())
                .as("context restored after exception").isTrue();
    }

    @Test
    void runIsolated_concurrentCalls_getDistinctContexts() throws Exception {
        int threads = 4;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int i = 0; i < threads; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    barrier.await();
                    var state = isolator.runIsolated(
                            () -> Arc.container().requestContext().getState());
                    assertThat(state).isNotNull();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            });
        }

        done.await();
        assertThat(failure.get()).isNull();
    }
}
