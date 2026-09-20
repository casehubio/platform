package io.casehub.platform.simulation.event;

import io.casehub.platform.simulation.TimedEntry;
import io.casehub.platform.simulation.TimedSequence;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventSequenceRunnerTest {

    @Test
    void runExecutesSequenceInOrder() throws InterruptedException {
        List<CloudEvent> emitted = new ArrayList<>();
        var runner = new EventSequenceRunner(emitted::add);

        var sequence = new TimedSequence<>(List.of(
                new TimedEntry<>(makeEvent("A"), Duration.ZERO),
                new TimedEntry<>(makeEvent("B"), Duration.ofMillis(10)),
                new TimedEntry<>(makeEvent("C"), Duration.ofMillis(10))));

        SequenceResult result = runner.run(sequence);

        assertThat(result.emittedCount()).isEqualTo(3);
        assertThat(result.hasFailures()).isFalse();
        assertThat(emitted).hasSize(3);
    }

    @Test
    void runStampsFreshIdPerEvent() throws InterruptedException {
        List<CloudEvent> emitted = new ArrayList<>();
        var runner = new EventSequenceRunner(emitted::add);

        CloudEvent template = makeEvent("same");
        var sequence = new TimedSequence<>(List.of(
                new TimedEntry<>(template, Duration.ZERO),
                new TimedEntry<>(template, Duration.ZERO)));

        runner.run(sequence);

        assertThat(emitted.get(0).getId()).isNotEqualTo(emitted.get(1).getId());
        assertThat(emitted.get(0).getTime()).isNotNull();
        assertThat(emitted.get(1).getTime()).isNotNull();
    }

    @Test
    void runIsolatesPerEventErrors() throws InterruptedException {
        List<CloudEvent> emitted = new ArrayList<>();
        var runner = new EventSequenceRunner(event -> {
            if (event.getType().equals("fail.event")) {
                throw new RuntimeException("deliberate failure");
            }
            emitted.add(event);
        });

        var sequence = new TimedSequence<>(List.of(
                new TimedEntry<>(makeEvent("ok"), Duration.ZERO),
                new TimedEntry<>(makeEventWithType("fail.event"), Duration.ZERO),
                new TimedEntry<>(makeEvent("also-ok"), Duration.ZERO)));

        SequenceResult result = runner.run(sequence);

        assertThat(result.emittedCount()).isEqualTo(2);
        assertThat(result.hasFailures()).isTrue();
        assertThat(result.failures()).hasSize(1);
        assertThat(emitted).hasSize(2);
    }

    @Test
    void runEmptySequence() throws InterruptedException {
        List<CloudEvent> emitted = new ArrayList<>();
        var runner = new EventSequenceRunner(emitted::add);

        SequenceResult result = runner.run(new TimedSequence<>(List.of()));

        assertThat(result.emittedCount()).isZero();
        assertThat(result.hasFailures()).isFalse();
    }

    @Test
    void runRespectsDelays() throws InterruptedException {
        List<Long> timestamps = new ArrayList<>();
        var runner = new EventSequenceRunner(event -> timestamps.add(System.nanoTime()));

        var sequence = new TimedSequence<>(List.of(
                new TimedEntry<>(makeEvent("A"), Duration.ZERO),
                new TimedEntry<>(makeEvent("B"), Duration.ofMillis(100))));

        runner.run(sequence);

        assertThat(timestamps).hasSize(2);
        long gapMs = (timestamps.get(1) - timestamps.get(0)) / 1_000_000;
        assertThat(gapMs).isGreaterThanOrEqualTo(80);
    }

    @Test
    void sequenceResultReports() {
        var result = new SequenceResult(List.of(), List.of());
        assertThat(result.hasFailures()).isFalse();
        assertThat(result.emittedCount()).isZero();
    }

    // --- helpers ---

    private static CloudEvent makeEvent(final String id) {
        return CloudEventBuilder.v1()
                .withId(id)
                .withType("test.event")
                .withSource(URI.create("/test"))
                .withExtension("tenancyid", "t1")
                .build();
    }

    private static CloudEvent makeEventWithType(final String type) {
        return CloudEventBuilder.v1()
                .withId("id")
                .withType(type)
                .withSource(URI.create("/test"))
                .withExtension("tenancyid", "t1")
                .build();
    }
}
