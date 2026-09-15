package io.casehub.platform.simulation.strategy;

import io.casehub.platform.simulation.KeyExtractor;
import io.casehub.platform.simulation.SimulationCorpus;
import io.casehub.platform.simulation.SimulationKeyNotFoundException;
import io.casehub.platform.simulation.SimulationStrategy;

public final class KeyLookupStrategy<I, O> implements SimulationStrategy<I, O> {

    private final SimulationCorpus<I, O> corpus;
    private final String qualifiedName;
    private final KeyExtractor<I> keyExtractor;

    public KeyLookupStrategy(final SimulationCorpus<I, O> corpus,
                             final String qualifiedName,
                             final KeyExtractor<I> keyExtractor) {
        this.corpus = corpus;
        this.qualifiedName = qualifiedName;
        this.keyExtractor = keyExtractor;
    }

    @Override
    public O resolve(final I input) {
        final String key = keyExtractor.extract(input);
        return corpus.lookupByKey(qualifiedName, key)
                .orElseThrow(() -> new SimulationKeyNotFoundException(key));
    }

    @Override
    public boolean canResolve(final I input) {
        final String key = keyExtractor.extract(input);
        return corpus.lookupByKey(qualifiedName, key).isPresent();
    }
}
