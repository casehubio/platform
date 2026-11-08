package io.casehub.platform.simulation.selector;

import io.casehub.platform.simulation.CorpusSelector;
import io.casehub.platform.simulation.CorpusSelectorConfig;

import java.util.List;
import java.util.Random;

public final class RandomSelector implements CorpusSelector {

    private final List<CorpusSelectorConfig.WeightedPhase> phases;
    private final Random random;

    public RandomSelector(List<CorpusSelectorConfig.WeightedPhase> phases, Random random) {
        this.phases = List.copyOf(phases);
        this.random = random;
    }

    @Override
    public String activePhase() {
        double r = random.nextDouble();
        double cumulative = 0;
        for (var phase : phases) {
            cumulative += phase.weight();
            if (r < cumulative) {
                return phase.name();
            }
        }
        return phases.getLast().name();
    }

    @Override
    public String type() {return "weighted";}

}
