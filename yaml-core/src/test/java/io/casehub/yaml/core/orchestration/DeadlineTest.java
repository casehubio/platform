package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class DeadlineTest {

    @Test
    void childScope_parentPrimitivesAccessibleViaChain() {
        var parent = new DefaultScenarioScope();
        var channel = parent.channel("shared");
        var child = parent.childScope("child");
        assertThat(child.channel("shared")).isSameAs(channel);
        parent.close();
    }

    @Test
    void childScope_close_doesNotReleaseParentPrimitives() {
        var parent = new DefaultScenarioScope();
        var latch = parent.latch("shared", 3);
        var child = parent.childScope("child");
        child.close();
        assertThat(latch.getCount()).isEqualTo(3);
        parent.close();
    }

    @Test
    void childScope_localPrimitiveCreation_notVisibleToParent() {
        var parent = new DefaultScenarioScope();
        var child = parent.childScope("child");
        child.counter("local-only");
        assertThat(parent.primitive("local-only", OrcCounter.class)).isNull();
        parent.close();
    }

    @Test
    void deadline_expiresAfterDuration_closesScope() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        var expired = new AtomicBoolean(false);
        var deadlined = scope.withDeadline(Duration.ofMillis(100), () -> expired.set(true));
        Thread.sleep(400);
        assertThat(expired.get()).isTrue();
        assertThat(deadlined.isDeadlineExpired()).isTrue();
        scope.close();
    }

    @Test
    void deadline_respectsSpeedMultiplier() throws InterruptedException {
        var scope = new DefaultScenarioScope(new DefaultPrimitiveFactory(), () -> 10.0);
        var expired = new AtomicBoolean(false);
        scope.withDeadline(Duration.ofSeconds(1), () -> expired.set(true));
        Thread.sleep(400);
        assertThat(expired.get()).isTrue();
        scope.close();
    }

    @Test
    void deadline_remainingTime_decreases() throws InterruptedException {
        var scope = new DefaultScenarioScope(new DefaultPrimitiveFactory(), () -> 100.0);
        var deadlined = scope.withDeadline(Duration.ofSeconds(5));
        Thread.sleep(200);
        var remaining = deadlined.remainingTime();
        assertThat(remaining).isPresent();
        assertThat(remaining.get().toMillis()).isLessThan(5000);
        scope.close();
    }

    @Test
    void deadline_remainingTime_emptyWhenNoDeadline() {
        var scope = new DefaultScenarioScope();
        assertThat(scope.remainingTime()).isEmpty();
        scope.close();
    }

    @Test
    void deadline_isDeadlineExpired_falseBeforeExpiry() {
        var scope = new DefaultScenarioScope();
        var deadlined = scope.withDeadline(Duration.ofSeconds(60));
        assertThat(deadlined.isDeadlineExpired()).isFalse();
        scope.close();
    }

    @Test
    void deadline_scopeClosedExternally_watcherExits() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        var expired = new AtomicBoolean(false);
        var deadlined = scope.withDeadline(Duration.ofSeconds(60), () -> expired.set(true));
        deadlined.close();
        Thread.sleep(100);
        assertThat(expired.get()).isFalse();
        scope.close();
    }

    @Test
    void deadline_withHandler_handlerThrows_scopeStillCloses() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        var deadlined = scope.withDeadline(Duration.ofMillis(50), () -> {
            throw new RuntimeException("handler failure");
        });
        Thread.sleep(300);
        assertThat(deadlined.isDeadlineExpired()).isTrue();
        scope.close();
    }

    @Test
    void deadline_isDeadlineExpired_childReportsParentDeadline() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        var deadlined = scope.withDeadline(Duration.ofMillis(100));
        var child = deadlined.childScope("nested");
        Thread.sleep(400);
        assertThat(child.isDeadlineExpired()).isTrue();
        scope.close();
    }

    @Test
    void childScope_parentPrimitivesCreatedAfterChild_visible() {
        var parent = new DefaultScenarioScope();
        var child  = parent.childScope("child");
        parent.counter("late-created");
        assertThat(child.primitive("late-created", OrcCounter.class)).isNotNull();
        parent.close();
    }

    @Test
    void deadline_blockedChannelReceive_interruptedOnExpiry() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        var deadlined = scope.withDeadline(Duration.ofMillis(100));
        OrcChannel<String> channel = deadlined.channel("data");

        var interrupted = new AtomicBoolean(false);
        deadlined.spawn("receiver", () -> {
            try {
                channel.receive();
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });

        Thread.sleep(300);
        assertThat(interrupted.get()).isTrue();
        scope.close();
    }

    @Test
    void deadline_spawnedThreads_interruptedOnExpiry() throws InterruptedException {
        var scope     = new DefaultScenarioScope();
        var deadlined = scope.withDeadline(Duration.ofMillis(100));

        var interrupted = new AtomicBoolean(false);
        deadlined.spawn("worker", () -> {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });

        Thread.sleep(300);
        assertThat(interrupted.get()).isTrue();
        assertThat(deadlined.isDeadlineExpired()).isTrue();
        scope.close();
    }

    @Test
    void deadline_primitivesCleaned_viaOrcPrimitive() throws InterruptedException {
        var scope     = new DefaultScenarioScope();
        var deadlined = scope.withDeadline(Duration.ofMillis(100));
        var latch     = deadlined.latch("gate", 5);
        var signal    = deadlined.signal("notify");

        Thread.sleep(300);
        assertThat(deadlined.isDeadlineExpired()).isTrue();
        assertThat(latch.getCount()).isEqualTo(0);
        assertThat(signal.isSignalled()).isTrue();
        scope.close();
    }

    @Test
    void deadline_childShorterDeadline_childClosesFirst() throws InterruptedException {
        var scope        = new DefaultScenarioScope();
        var parent       = scope.withDeadline(Duration.ofSeconds(60));
        var childExpired = new AtomicBoolean(false);
        parent.withDeadline(Duration.ofMillis(100), () -> childExpired.set(true));

        Thread.sleep(300);
        assertThat(childExpired.get()).isTrue();
        assertThat(parent.isDeadlineExpired()).isFalse();
        scope.close();
    }

    @Test
    void deadline_parentDeadline_childClosesWithParent() throws InterruptedException {
        var scope         = new DefaultScenarioScope();
        var parentExpired = new AtomicBoolean(false);
        var parent        = scope.withDeadline(Duration.ofMillis(100), () -> parentExpired.set(true));
        var childLatch    = parent.latch("child-gate", 3);

        Thread.sleep(300);
        assertThat(parentExpired.get()).isTrue();
        assertThat(childLatch.getCount()).isEqualTo(0);
        scope.close();
    }

    @Test
    void deadline_nestedDeadlines_childReportsOwnDeadline() throws InterruptedException {
        var scope  = new DefaultScenarioScope(new DefaultPrimitiveFactory(), () -> 10.0);
        var parent = scope.withDeadline(Duration.ofSeconds(60));
        var child  = parent.withDeadline(Duration.ofSeconds(30));

        Thread.sleep(200);
        var childRemaining  = child.remainingTime();
        var parentRemaining = parent.remainingTime();
        assertThat(childRemaining).isPresent();
        assertThat(parentRemaining).isPresent();
        assertThat(childRemaining.get().toMillis()).isLessThan(parentRemaining.get().toMillis());
        scope.close();
    }
}
