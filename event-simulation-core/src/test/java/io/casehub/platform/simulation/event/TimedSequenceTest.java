package io.casehub.platform.simulation.event;

import io.casehub.platform.simulation.InvocationRecord;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimedSequenceTest {

    @Test
    void timedEntryRejectsNullEvent() {
        assertThatThrownBy(() -> new TimedEntry<>(null, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timedEntryRejectsNegativeDelay() {
        assertThatThrownBy(() -> new TimedEntry<>("event", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptySequence() {
        var seq = new TimedSequence<>(List.of());
        assertThat(seq.size()).isZero();
        assertThat(seq.totalDuration()).isEqualTo(Duration.ZERO);
    }

    @Test
    void sequencePreservesOrder() {
        var seq = new TimedSequence<>(List.of(
                new TimedEntry<>("A", Duration.ZERO),
                new TimedEntry<>("B", Duration.ofSeconds(5)),
                new TimedEntry<>("C", Duration.ofSeconds(30))));

        assertThat(seq.size()).isEqualTo(3);
        assertThat(seq.entries().get(0).event()).isEqualTo("A");
        assertThat(seq.entries().get(1).delay()).isEqualTo(Duration.ofSeconds(5));
        assertThat(seq.entries().get(2).delay()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void totalDurationSumsDelays() {
        var seq = new TimedSequence<>(List.of(
                new TimedEntry<>("A", Duration.ZERO),
                new TimedEntry<>("B", Duration.ofSeconds(5)),
                new TimedEntry<>("C", Duration.ofSeconds(30))));

        assertThat(seq.totalDuration()).isEqualTo(Duration.ofSeconds(35));
    }

    @Test
    void withMultiplierScalesDelays() {
        var seq = new TimedSequence<>(List.of(
                new TimedEntry<>("A", Duration.ofSeconds(10)),
                new TimedEntry<>("B", Duration.ofSeconds(30))));

        var fast = seq.withMultiplier(10.0);

        assertThat(fast.entries().get(0).delay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(fast.entries().get(1).delay()).isEqualTo(Duration.ofSeconds(3));
        assertThat(fast.size()).isEqualTo(2);
    }

    @Test
    void withMultiplierRejectsZero() {
        var seq = new TimedSequence<>(List.of(new TimedEntry<>("A", Duration.ZERO)));
        assertThatThrownBy(() -> seq.withMultiplier(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withMultiplierRejectsNegative() {
        var seq = new TimedSequence<>(List.of(new TimedEntry<>("A", Duration.ZERO)));
        assertThatThrownBy(() -> seq.withMultiplier(-1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromRecordedDerivesTiming() {
        var records = List.of(
                new InvocationRecord<>("t1", null, "inputA", "outputA",
                        Instant.parse("2026-09-16T10:00:00Z")),
                new InvocationRecord<>("t1", null, "inputB", "outputB",
                        Instant.parse("2026-09-16T10:00:05Z")),
                new InvocationRecord<>("t1", null, "inputC", "outputC",
                        Instant.parse("2026-09-16T10:00:35Z")));

        var sequence = TimedSequence.<String, String>fromRecorded(records);

        assertThat(sequence.size()).isEqualTo(3);
        assertThat(sequence.entries().get(0).event()).isEqualTo("outputA");
        assertThat(sequence.entries().get(0).delay()).isEqualTo(Duration.ZERO);
        assertThat(sequence.entries().get(1).event()).isEqualTo("outputB");
        assertThat(sequence.entries().get(1).delay()).isEqualTo(Duration.ofSeconds(5));
        assertThat(sequence.entries().get(2).event()).isEqualTo("outputC");
        assertThat(sequence.entries().get(2).delay()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void fromRecordedSortsByTimestamp() {
        var records = List.of(
                new InvocationRecord<>("t1", null, "in2", "second",
                        Instant.parse("2026-09-16T10:00:10Z")),
                new InvocationRecord<>("t1", null, "in1", "first",
                        Instant.parse("2026-09-16T10:00:00Z")));

        var sequence = TimedSequence.<String, String>fromRecorded(records);

        assertThat(sequence.entries().get(0).event()).isEqualTo("first");
        assertThat(sequence.entries().get(1).event()).isEqualTo("second");
        assertThat(sequence.entries().get(1).delay()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void fromRecordedEmptyList() {
        var sequence = TimedSequence.<String, String>fromRecorded(List.of());
        assertThat(sequence.size()).isZero();
    }

    @Test
    void entriesListIsDefensivelyCopied() {
        var entries = new ArrayList<TimedEntry<String>>();
        entries.add(new TimedEntry<>("A", Duration.ZERO));
        var seq = new TimedSequence<>(entries);
        entries.add(new TimedEntry<>("B", Duration.ofSeconds(1)));
        assertThat(seq.size()).isEqualTo(1);
    }
}
