package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ChildScopeTest {

    @Test
    void childScope_inheritsPrimitiveNamespace() {
        var parent = new DefaultScenarioScope();
        var counter = parent.counter("events");
        counter.increment();

        var child = parent.childScope("sub");
        var childCounter = child.primitive("events", OrcCounter.class);

        assertThat(childCounter).isSameAs(counter);
        assertThat(childCounter.get()).isEqualTo(1);
        parent.close();
    }

    @Test
    void childScope_closingParent_closesChild() throws InterruptedException {
        var parent = new DefaultScenarioScope();
        var child = parent.childScope("sub");
        var interrupted = new AtomicBoolean(false);
        var started = new CountDownLatch(1);

        child.spawn("child-worker", () -> {
            started.countDown();
            try { Thread.sleep(60_000); } catch (InterruptedException e) { interrupted.set(true); }
        });

        started.await(1, TimeUnit.SECONDS);
        parent.close();
        Thread.sleep(200);
        assertThat(interrupted.get()).isTrue();
    }

    @Test
    void childScope_nestedChildScopes() throws InterruptedException {
        var parent = new DefaultScenarioScope();
        var child = parent.childScope("level-1");
        var grandchild = child.childScope("level-2");

        var interrupted = new AtomicBoolean(false);
        var started = new CountDownLatch(1);

        grandchild.spawn("deep-worker", () -> {
            started.countDown();
            try { Thread.sleep(60_000); } catch (InterruptedException e) { interrupted.set(true); }
        });

        started.await(1, TimeUnit.SECONDS);
        parent.close();
        Thread.sleep(200);
        assertThat(interrupted.get()).isTrue();
    }

    @Test
    void childScope_childCanCreateOwnPrimitives() {
        var parent = new DefaultScenarioScope();
        var child = parent.childScope("sub");

        var childCounter = child.counter("child-only");
        childCounter.increment();
        assertThat(childCounter.get()).isEqualTo(1);

        assertThat(parent.primitive("child-only", OrcCounter.class)).isNull();
        parent.close();
    }

    @Test
    void childScope_sharedChannel_parentAndChildCommunicate() throws InterruptedException {
        var parent = new DefaultScenarioScope();
        OrcChannel<String> ch = parent.channel("shared");
        var child = parent.childScope("sub");
        var received = new AtomicBoolean(false);
        var started = new CountDownLatch(1);

        child.spawn("receiver", () -> {
            started.countDown();
            try {
                OrcChannel<String> childCh = child.primitive("shared", OrcChannel.class);
                String msg = childCh.receive();
                if ("hello".equals(msg)) received.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        started.await(1, TimeUnit.SECONDS);
        Thread.sleep(50);
        ch.send("hello");
        Thread.sleep(200);
        assertThat(received.get()).isTrue();
        parent.close();
    }
}
