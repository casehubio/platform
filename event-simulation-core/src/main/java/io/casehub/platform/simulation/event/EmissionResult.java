package io.casehub.platform.simulation.event;

import java.util.List;

public record EmissionResult(
        List<EmittedEvent> emitted,
        List<EmissionFailure> failures) {

    public EmissionResult {
        emitted = List.copyOf(emitted);
        failures = List.copyOf(failures);
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    public int emittedCount() {
        return emitted.size();
    }
}
