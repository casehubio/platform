package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioScopeTest {

    @Test
    void semaphore_sameNameReturnsSameInstance() {
        var scope = new DefaultScenarioScope();
        var s1 = scope.semaphore("api", 3);
        var s2 = scope.semaphore("api", 3);
        assertThat(s1).isSameAs(s2);
        scope.close();
    }

    @Test
    void semaphore_differentNameReturnsDifferentInstance() {
        var scope = new DefaultScenarioScope();
        var s1 = scope.semaphore("api", 3);
        var s2 = scope.semaphore("db", 5);
        assertThat(s1).isNotSameAs(s2);
        scope.close();
    }

    @Test
    void latch_createdWithCorrectCount() {
        var scope = new DefaultScenarioScope();
        var latch = scope.latch("barrier", 3);
        assertThat(latch.getCount()).isEqualTo(3);
        scope.close();
    }

    @Test
    void signal_createdAndRetrievable() {
        var scope = new DefaultScenarioScope();
        var signal = scope.signal("ready");
        assertThat(signal).isNotNull();
        assertThat(signal.isSignalled()).isFalse();
        signal.signal("data");
        assertThat(signal.isSignalled()).isTrue();
        scope.close();
    }

    @Test
    void channel_unboundedByDefault() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        OrcChannel<String> ch = scope.channel("events");
        for (int i = 0; i < 100; i++) {
            ch.send("item-" + i);
        }
        assertThat(ch.isEmpty()).isFalse();
        scope.close();
    }

    @Test
    void channel_boundedWithCapacity() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        OrcChannel<String> ch = scope.channel("bounded", 5);
        for (int i = 0; i < 5; i++) {
            ch.send("item-" + i);
        }
        scope.close();
    }

    @Test
    void close_disposesAllPrimitives() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        var latch = scope.latch("wait", 1);
        var signal = scope.signal("notify");
        OrcChannel<String> ch = scope.channel("data");

        var unblocked = new java.util.concurrent.atomic.AtomicBoolean(false);
        Thread.ofVirtual().name("waiter").start(() -> {
            try {
                latch.await();
                unblocked.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.sleep(50);
        assertThat(unblocked.get()).isFalse();
        scope.close();
        Thread.sleep(50);
        assertThat(unblocked.get()).isTrue();
    }

    @Test
    void resultStore_returnsSameInstance() {
        var scope = new DefaultScenarioScope();
        assertThat(scope.resultStore()).isSameAs(scope.resultStore());
        scope.close();
    }

    @Test
    void primitive_genericLookup() {
        var scope = new DefaultScenarioScope();
        scope.semaphore("test", 2);
        var found = scope.primitive("test", OrcSemaphore.class);
        assertThat(found).isNotNull();
        scope.close();
    }

    @Test
    void concurrentAccess_sameNameSameInstance() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        var refs = new AtomicReference[10];
        var done = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            final int idx = i;
            refs[idx] = new AtomicReference<>();
            Thread.ofVirtual().name("accessor-" + i).start(() -> {
                refs[idx].set(scope.semaphore("shared", 3));
                done.countDown();
            });
        }

        done.await(2, TimeUnit.SECONDS);
        var first = refs[0].get();
        for (int i = 1; i < 10; i++) {
            assertThat(refs[i].get()).isSameAs(first);
        }
        scope.close();
    }
}
