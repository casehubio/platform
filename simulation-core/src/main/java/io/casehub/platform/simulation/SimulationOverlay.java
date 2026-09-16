package io.casehub.platform.simulation;

import java.util.concurrent.ConcurrentHashMap;

public final class SimulationOverlay {

    private final SimulationConfig config;
    private final SimulationCorpus<?, ?> corpus;
    private final InvocationJournal journal;
    private final ConcurrentHashMap<String, SimulationStrategy<?, ?>> strategyCache;

    SimulationOverlay(final SimulationConfig config, final SimulationCorpus<?, ?> corpus) {
        this.config = config;
        this.corpus = corpus;
        this.journal = new InvocationJournal();
        this.strategyCache = new ConcurrentHashMap<>();
    }

    public SimulationConfig config() {
        return config;
    }

    @SuppressWarnings("rawtypes")
    public SimulationCorpus corpus() {
        return (SimulationCorpus) corpus;
    }

    public InvocationJournal journal() {
        return journal;
    }

    ConcurrentHashMap<String, SimulationStrategy<?, ?>> strategyCache() {
        return strategyCache;
    }
}
