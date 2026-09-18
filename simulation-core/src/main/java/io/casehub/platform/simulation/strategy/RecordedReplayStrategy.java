package io.casehub.platform.simulation.strategy;

import io.casehub.platform.simulation.KeyExtractor;
import io.casehub.platform.simulation.SimulationCorpus;
import io.casehub.platform.simulation.SimulationExhaustedException;
import io.casehub.platform.simulation.SimulationStrategy;

import java.util.concurrent.atomic.AtomicInteger;

public final class RecordedReplayStrategy<I, O> implements SimulationStrategy<I, O> {

    private final SimulationCorpus<I, O> corpus;
    private final String qualifiedName;
    private final KeyExtractor<I> keyExtractor;
    private final AtomicInteger fallbackIndex = new AtomicInteger(0);

    public RecordedReplayStrategy(final SimulationCorpus<I, O> corpus,
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
                .orElseGet(() -> {
                    final int i = fallbackIndex.getAndIncrement();
                    final int size = corpus.size(qualifiedName);
                    if (size == 0) {
                        throw new SimulationExhaustedException(
                                "Corpus is empty for " + qualifiedName);
                    }
                    return corpus.lookupByIndex(qualifiedName, i % size)
                            .orElseThrow(() -> new SimulationExhaustedException(
                                    "No entry at index " + (i % size) + " for " + qualifiedName));
                });
    }

    @Override
    public boolean canResolve(final I input) {
        return corpus.size(qualifiedName) > 0;
    }
}
