package io.casehub.platform.simulation.event.quarkus;

public record TemporalDriverStatus(
        String name,
        String profileName,
        String state,
        double speed,
        int emittedCount,
        int failureCount,
        int loopIterations,
        boolean hasFailures) {}
