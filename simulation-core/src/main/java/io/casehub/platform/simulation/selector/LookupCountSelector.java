package io.casehub.platform.simulation.selector;

import io.casehub.platform.simulation.CorpusSelector;
import io.casehub.platform.simulation.CorpusSelectorConfig;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class LookupCountSelector implements CorpusSelector {

    private final List<CorpusSelectorConfig.CountPhase> phases;
    private final AtomicInteger resolveCount = new AtomicInteger(0);

    public LookupCountSelector(List<CorpusSelectorConfig.CountPhase> phases) {
        this.phases = List.copyOf(phases);
    }

    @Override
    public String activePhase() {
        int count = resolveCount.get();
        int cumulative = 0;
        for (var phase : phases) {
            if (phase.count() <= 0 || count < cumulative + phase.count()) {
                return phase.name();
            }
            cumulative += phase.count();
        }
        return phases.getLast().name();
    }

    @Override
    public void onResolved() {
        resolveCount.incrementAndGet();
    }
}
