package io.casehub.platform.simulation.event;

import io.casehub.platform.simulation.InvocationRecord;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record TimedSequence<E>(List<TimedEntry<E>> entries) {

    public TimedSequence {
        entries = List.copyOf(entries);
    }

    public TimedSequence<E> withMultiplier(final double multiplier) {
        if (multiplier <= 0) {
            throw new IllegalArgumentException("multiplier must be positive");
        }
        return new TimedSequence<>(entries.stream()
                .map(e -> new TimedEntry<>(e.event(),
                        Duration.ofMillis((long) (e.delay().toMillis() / multiplier))))
                .toList());
    }

    public Duration totalDuration() {
        return entries.stream()
                .map(TimedEntry::delay)
                .reduce(Duration.ZERO, Duration::plus);
    }

    public int size() {
        return entries.size();
    }

    public static <I, O> TimedSequence<O> fromRecorded(final List<InvocationRecord<I, O>> records) {
        if (records.isEmpty()) {
            return new TimedSequence<>(List.of());
        }

        final List<InvocationRecord<I, O>> sorted = records.stream()
                .sorted(Comparator.comparing(InvocationRecord::recordedAt))
                .toList();

        final List<TimedEntry<O>> entries = new ArrayList<>();
        entries.add(new TimedEntry<>(sorted.get(0).output(), Duration.ZERO));

        for (int i = 1; i < sorted.size(); i++) {
            Duration gap = Duration.between(
                    sorted.get(i - 1).recordedAt(),
                    sorted.get(i).recordedAt());
            if (gap.isNegative()) {
                gap = Duration.ZERO;
            }
            entries.add(new TimedEntry<>(sorted.get(i).output(), gap));
        }

        return new TimedSequence<>(entries);
    }
}
