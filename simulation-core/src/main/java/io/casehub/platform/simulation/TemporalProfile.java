package io.casehub.platform.simulation;

import java.util.Objects;

public record TemporalProfile<E>(
        String name,
        String qualifiedName,
        String tenancyId,
        TimedSequence<E> sequence,
        boolean loop,
        double speed) {

    public TemporalProfile {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(qualifiedName, "qualifiedName");
        Objects.requireNonNull(sequence, "sequence");
        if (speed <= 0) throw new IllegalArgumentException("speed must be positive");
    }
}
