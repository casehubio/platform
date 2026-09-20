package io.casehub.platform.simulation;

import java.util.List;

public record DriverResult(
        int emittedCount,
        int failureCount,
        int loopIterations,
        List<DriverFailure> failures) {

    public DriverResult {
        failures = List.copyOf(failures);
    }

    public boolean hasFailures() {
        return failureCount > 0;
    }
}
