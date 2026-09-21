package io.casehub.platform.simulation;

import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemporalSimulationDriverTest {

    @Test
    void basicRunDeliversAllEvents() throws InterruptedException {
        var delivered = new ArrayList<String>();
        var latch = new CountDownLatch(3);
        TemporalEventSink<String> sink = (qn, label, event) -> {
            delivered.add(event);
            latch.countDown();
        };

        var profile = new TemporalProfile<>("test", "my.method", null,
                new TimedSequence<>(List.of(
                        new TimedEntry<>("A", Duration.ZERO, "step-a"),
                        new TimedEntry<>("B", Duration.ofMillis(10), "step-b"),
                        new TimedEntry<>("C", Duration.ofMillis(10), "step-c"))),
                false, 1.0);

        var driver = new TemporalSimulationDriver<>(sink);
        driver.start(profile);
        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(50);

        assertThat(delivered).containsExactly("A", "B", "C");
        assertThat(driver.state()).isEqualTo(TemporalSimulationDriver.State.COMPLETED);
        assertThat(driver.isRunning()).isFalse();

        var result = driver.lastResult();
        assertThat(result.emittedCount()).isEqualTo(3);
        assertThat(result.hasFailures()).isFalse();
        assertThat(result.loopIterations()).isEqualTo(1);
    }

    @Test
    void loopingRepeatsSequence() throws InterruptedException {
        var delivered = new ArrayList<String>();
        var latch = new CountDownLatch(6);
        TemporalEventSink<String> sink = (qn, label, event) -> {
            delivered.add(event);
            latch.countDown();
        };

        var profile = new TemporalProfile<>("loop-test", "my.method", null,
                new TimedSequence<>(List.of(
                        new TimedEntry<>("A", Duration.ZERO),
                        new TimedEntry<>("B", Duration.ofMillis(10)))),
                true, 1.0);

        var driver = new TemporalSimulationDriver<>(sink);
        driver.start(profile);
        latch.await(5, TimeUnit.SECONDS);
        driver.stop();

        assertThat(delivered.size()).isGreaterThanOrEqualTo(6);
        assertThat(delivered.get(0)).isEqualTo("A");
        assertThat(delivered.get(1)).isEqualTo("B");
        assertThat(delivered.get(2)).isEqualTo("A");
        assertThat(driver.lastResult().loopIterations()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void pauseStopsDeliveryResumeRestarts() throws InterruptedException {
        var delivered = new ArrayList<String>();
        TemporalEventSink<String> sink = (qn, label, event) -> delivered.add(event);

        var profile = new TemporalProfile<>("pause-test", "my.method", null,
                new TimedSequence<>(List.of(
                        new TimedEntry<>("A", Duration.ZERO),
                        new TimedEntry<>("B", Duration.ofMillis(200)),
                        new TimedEntry<>("C", Duration.ofMillis(10)))),
                false, 1.0);

        var driver = new TemporalSimulationDriver<>(sink);
        driver.start(profile);
        Thread.sleep(50);
        driver.pause();
        assertThat(driver.state()).isEqualTo(TemporalSimulationDriver.State.PAUSED);
        int countAtPause = delivered.size();
        Thread.sleep(100);
        assertThat(delivered.size()).isEqualTo(countAtPause);

        driver.resume();
        Thread.sleep(500);
        assertThat(driver.state()).isEqualTo(TemporalSimulationDriver.State.COMPLETED);
        assertThat(delivered).containsExactly("A", "B", "C");
    }

    @Test
    void stopTerminatesDriver() throws InterruptedException {
        var delivered = new ArrayList<String>();
        TemporalEventSink<String> sink = (qn, label, event) -> delivered.add(event);

        var profile = new TemporalProfile<>("stop-test", "my.method", null,
                new TimedSequence<>(List.of(
                        new TimedEntry<>("A", Duration.ZERO),
                        new TimedEntry<>("B", Duration.ofMillis(500)),
                        new TimedEntry<>("C", Duration.ofMillis(10)))),
                false, 1.0);

        var driver = new TemporalSimulationDriver<>(sink);
        driver.start(profile);
        Thread.sleep(50);
        driver.stop();
        Thread.sleep(100);

        assertThat(driver.state()).isEqualTo(TemporalSimulationDriver.State.STOPPED);
        assertThat(delivered).containsExactly("A");
    }

    @Test
    void speedScalesDelays() throws InterruptedException {
        var timestamps = new ArrayList<Long>();
        var latch = new CountDownLatch(2);
        TemporalEventSink<String> sink = (qn, label, event) -> {
            timestamps.add(System.nanoTime());
            latch.countDown();
        };

        var profile = new TemporalProfile<>("speed-test", "my.method", null,
                new TimedSequence<>(List.of(
                        new TimedEntry<>("A", Duration.ZERO),
                        new TimedEntry<>("B", Duration.ofMillis(500)))),
                false, 10.0);

        var driver = new TemporalSimulationDriver<>(sink);
        driver.start(profile);
        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(50);

        long gapMs = (timestamps.get(1) - timestamps.get(0)) / 1_000_000;
        assertThat(gapMs).isBetween(20L, 150L);
    }

    @Test
    void setSpeedChangesNextDelay() throws InterruptedException {
        var timestamps = new ArrayList<Long>();
        var latch = new CountDownLatch(3);
        TemporalEventSink<String> sink = (qn, label, event) -> {
            timestamps.add(System.nanoTime());
            latch.countDown();
        };

        var profile = new TemporalProfile<>("setspeed-test", "my.method", null,
                new TimedSequence<>(List.of(
                        new TimedEntry<>("A", Duration.ZERO),
                        new TimedEntry<>("B", Duration.ofMillis(500)),
                        new TimedEntry<>("C", Duration.ofMillis(500)))),
                false, 1.0);

        var driver = new TemporalSimulationDriver<>(sink);
        driver.start(profile);
        Thread.sleep(20);
        driver.setSpeed(100.0);
        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(50);

        long gap2Ms = (timestamps.get(2) - timestamps.get(1)) / 1_000_000;
        assertThat(gap2Ms).isLessThan(100);
    }

    @Test
    void errorIsolationContinuesSequence() throws InterruptedException {
        var delivered = new ArrayList<String>();
        var latch = new CountDownLatch(2);
        TemporalEventSink<String> sink = (qn, label, event) -> {
            if ("B".equals(event)) throw new RuntimeException("deliberate");
            delivered.add(event);
            latch.countDown();
        };

        var profile = new TemporalProfile<>("error-test", "my.method", null,
                new TimedSequence<>(List.of(
                        new TimedEntry<>("A", Duration.ZERO, "step-a"),
                        new TimedEntry<>("B", Duration.ofMillis(10), "step-b"),
                        new TimedEntry<>("C", Duration.ofMillis(10), "step-c"))),
                false, 1.0);

        var driver = new TemporalSimulationDriver<>(sink);
        driver.start(profile);
        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(50);

        assertThat(delivered).containsExactly("A", "C");
        var result = driver.lastResult();
        assertThat(result.emittedCount()).isEqualTo(2);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).index()).isEqualTo(1);
        assertThat(result.failures().get(0).label()).isEqualTo("step-b");
    }

    @Test
    void journalRecordsWithOverlay() throws InterruptedException {
        var runtime = new SimulationRuntime(
                MapSimulationConfig.of(Map.of()),
                new InMemorySimulationCorpus<>());
        var overlay = runtime.pushOverlay(
                MapSimulationConfig.of(Map.of()));

        var latch = new CountDownLatch(2);
        TemporalEventSink<String> sink = (qn, label, event) -> latch.countDown();

        var profile = new TemporalProfile<>("journal-test", "my.method", "t1",
                new TimedSequence<>(List.of(
                        new TimedEntry<>("A", Duration.ZERO, "step-a"),
                        new TimedEntry<>("B", Duration.ofMillis(10), "step-b"))),
                false, 1.0);

        var driver = new TemporalSimulationDriver<>(sink, runtime);
        driver.start(profile);
        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(50);
        driver.stop();

        var entries = runtime.journal(overlay);
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).qualifiedName()).isEqualTo("my.method");
        assertThat(entries.get(0).tenancyId()).isEqualTo("t1");

        runtime.popOverlay(overlay);
    }

    @Test
    void startOnNonIdleThrows() {
        TemporalEventSink<String> sink = (qn, label, event) -> {};
        var profile = new TemporalProfile<>("test", "qn", null,
                new TimedSequence<>(List.of(new TimedEntry<>("A", Duration.ofSeconds(60)))),
                false, 1.0);

        var driver = new TemporalSimulationDriver<>(sink);
        driver.start(profile);
        assertThatThrownBy(() -> driver.start(profile))
                .isInstanceOf(IllegalStateException.class);
        driver.stop();
    }

    @Test
    void setSpeedRejectsZero() {
        TemporalEventSink<String> sink = (qn, label, event) -> {};
        var driver = new TemporalSimulationDriver<>(sink);
        assertThatThrownBy(() -> driver.setSpeed(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concatQualifiedNamesPreserved() throws InterruptedException {
        var qualifiedNames = new ArrayList<String>();
        var latch = new CountDownLatch(2);
        TemporalEventSink<String> sink = (qn, label, event) -> {
            qualifiedNames.add(qn);
            latch.countDown();
        };

        var profile = new TemporalProfile<>("concat-test", "parent.method", null,
                new TimedSequence<>(List.of(
                        new TimedEntry<>("A", Duration.ZERO, "a", "child.method"),
                        new TimedEntry<>("B", Duration.ofMillis(10), "b", null))),
                false, 1.0);

        var driver = new TemporalSimulationDriver<>(sink);
        driver.start(profile);
        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(50);

        assertThat(qualifiedNames.get(0)).isEqualTo("child.method");
        assertThat(qualifiedNames.get(1)).isEqualTo("parent.method");
    }

    @Test
    void speedReturnsCurrentSpeed() throws InterruptedException {
        TemporalEventSink<String> sink = (qn, label, event) -> {};
        var profile = new TemporalProfile<>("speed-test", "my.method", null,
                                            new TimedSequence<>(List.of(
                                                    new TimedEntry<>("A", Duration.ofMillis(500)))),
                                            false, 5.0);

        var driver = new TemporalSimulationDriver<>(sink);
        assertThat(driver.speed()).isEqualTo(1.0);

        driver.start(profile);
        assertThat(driver.speed()).isEqualTo(5.0);

        driver.setSpeed(10.0);
        assertThat(driver.speed()).isEqualTo(10.0);

        driver.stop();
    }

    @Test
    void globalSpeedComposesWithProfileSpeed() throws InterruptedException {
        var config  = MapSimulationConfig.builder().speed(2.0).build();
        var runtime = new SimulationRuntime(config, new InMemorySimulationCorpus<>());
        var events  = new ArrayList<String>();
        var latch   = new CountDownLatch(2);
        var driver = new TemporalSimulationDriver<String>(
                (qn, label, event) -> {
                    events.add(event);
                    latch.countDown();
                }, runtime);

        var profile = new TemporalProfile<>("test", "test.qn", null,
                                            new TimedSequence<>(List.of(
                                                    new TimedEntry<>("a", Duration.ofMillis(50)),
                                                    new TimedEntry<>("b", Duration.ofMillis(50)))),
                                            false, 10.0);

        driver.start(profile);
        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(50);
        driver.stop();

        assertThat(events).containsExactly("a", "b");
        assertThat(driver.speed()).isEqualTo(20.0);
    }

    @Test
    void localOverrideTakesPrecedence() throws InterruptedException {
        var config  = MapSimulationConfig.builder().speed(2.0).build();
        var runtime = new SimulationRuntime(config, new InMemorySimulationCorpus<>());
        var driver = new TemporalSimulationDriver<String>(
                (qn, label, event) -> {}, runtime);

        var profile = new TemporalProfile<>("test", "test.qn", null,
                                            new TimedSequence<>(List.of(new TimedEntry<>("a", Duration.ofMillis(10)))),
                                            false, 10.0);

        driver.start(profile);
        driver.setSpeed(5.0);
        assertThat(driver.speed()).isEqualTo(5.0);
        driver.stop();
    }

    @Test
    void resetSpeedRevertsToGlobalComposition() throws InterruptedException {
        var config  = MapSimulationConfig.builder().speed(2.0).build();
        var runtime = new SimulationRuntime(config, new InMemorySimulationCorpus<>());
        var driver = new TemporalSimulationDriver<String>(
                (qn, label, event) -> {}, runtime);

        var profile = new TemporalProfile<>("test", "test.qn", null,
                                            new TimedSequence<>(List.of(new TimedEntry<>("a", Duration.ofMillis(10)))),
                                            false, 10.0);

        driver.start(profile);
        driver.setSpeed(5.0);
        assertThat(driver.speed()).isEqualTo(5.0);
        driver.resetSpeed();
        assertThat(driver.speed()).isEqualTo(20.0);
        driver.stop();
    }

    @Test
    void reactiveGlobalSpeedSync() throws InterruptedException {
        var config  = MapSimulationConfig.builder().speed(1.0).build();
        var runtime = new SimulationRuntime(config, new InMemorySimulationCorpus<>());
        var driver = new TemporalSimulationDriver<String>(
                (qn, label, event) -> {}, runtime);

        var profile = new TemporalProfile<>("test", "test.qn", null,
                                            new TimedSequence<>(List.of(new TimedEntry<>("a", Duration.ofMillis(10)))),
                                            true, 10.0);

        driver.start(profile);
        assertThat(driver.speed()).isEqualTo(10.0);

        runtime.setGlobalSpeed(3.0);
        assertThat(driver.speed()).isEqualTo(30.0);

        driver.stop();
    }

    @Test
    void preStartSpeedReflectsGlobal() {
        var config  = MapSimulationConfig.builder().speed(5.0).build();
        var runtime = new SimulationRuntime(config, new InMemorySimulationCorpus<>());
        var driver = new TemporalSimulationDriver<String>(
                (qn, label, event) -> {}, runtime);

        assertThat(driver.speed()).isEqualTo(5.0);
    }

    @Test
    void nullRuntimeFallsBackToOne() throws InterruptedException {
        var driver = new TemporalSimulationDriver<String>(
                (qn, label, event) -> {});

        var profile = new TemporalProfile<>("test", "test.qn", null,
                                            new TimedSequence<>(List.of(new TimedEntry<>("a", Duration.ofMillis(10)))),
                                            false, 10.0);

        driver.start(profile);
        assertThat(driver.speed()).isEqualTo(10.0);
        driver.stop();
    }

}
