package io.casehub.platform.simulation.selector;

import io.casehub.platform.simulation.CorpusSelector;
import io.casehub.platform.simulation.CorpusSelectorConfig;
import io.casehub.yaml.core.runtime.SpeedMultiplier;

import java.util.List;
import java.util.function.LongSupplier;

public final class DurationSelector implements CorpusSelector {

    private final List<CorpusSelectorConfig.DurationPhase> phases;
    private final SpeedMultiplier speedMultiplier;
    private final LongSupplier nanoSource;
    private final long startNanos;

    public DurationSelector(List<CorpusSelectorConfig.DurationPhase> phases,
                            SpeedMultiplier speedMultiplier) {
        this(phases, speedMultiplier, System::nanoTime);
    }

    DurationSelector(List<CorpusSelectorConfig.DurationPhase> phases,
                     SpeedMultiplier speedMultiplier,
                     LongSupplier nanoSource) {
        this.phases = List.copyOf(phases);
        this.speedMultiplier = speedMultiplier;
        this.nanoSource = nanoSource;
        this.startNanos = nanoSource.getAsLong();
    }

    @Override
    public String activePhase() {
        long elapsed = nanoSource.getAsLong() - startNanos;
        double speed = speedMultiplier.currentSpeed();
        long simulatedNanos = (long) (elapsed * speed);

        long cumulative = 0;
        for (var phase : phases) {
            if (phase.duration() == null || simulatedNanos < cumulative + phase.duration().toNanos()) {
                return phase.name();
            }
            cumulative += phase.duration().toNanos();
        }
        return phases.getLast().name();
    }
}
