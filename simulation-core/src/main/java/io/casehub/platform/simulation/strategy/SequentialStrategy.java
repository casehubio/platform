package io.casehub.platform.simulation.strategy;

import io.casehub.platform.simulation.ExhaustionPolicy;
import io.casehub.platform.simulation.SimulationCorpus;
import io.casehub.platform.simulation.SimulationExhaustedException;
import io.casehub.platform.simulation.SimulationStrategy;

import java.util.concurrent.atomic.AtomicInteger;

public final class SequentialStrategy<I, O> implements SimulationStrategy<I, O> {

    private final SimulationCorpus<I, O> corpus;
    private final String qualifiedName;
    private final ExhaustionPolicy exhaustionPolicy;
    private final AtomicInteger index = new AtomicInteger(0);

    public SequentialStrategy(final SimulationCorpus<I, O> corpus,
                              final String qualifiedName,
                              final ExhaustionPolicy exhaustionPolicy) {
        this.corpus = corpus;
        this.qualifiedName = qualifiedName;
        this.exhaustionPolicy = exhaustionPolicy;
    }

    @Override
    public O resolve(final I input) {
        final int size = corpus.size(qualifiedName);
        if (size == 0) {
            throw new SimulationExhaustedException(
                    "Corpus is empty for " + qualifiedName);
        }
        final int i = index.getAndIncrement();
        if (exhaustionPolicy == ExhaustionPolicy.THROW && i >= size) {
            throw new SimulationExhaustedException(
                    "Corpus exhausted at index " + i + " (size: " + size + ") for " + qualifiedName);
        }
        return corpus.lookupByIndex(qualifiedName, i % size)
                .orElseThrow(() -> new SimulationExhaustedException(
                        "No entry at index " + (i % size) + " for " + qualifiedName));
    }

    @Override
    public boolean canResolve(final I input) {
        final int size = corpus.size(qualifiedName);
        if (size == 0) {
            return false;
        }
        if (exhaustionPolicy == ExhaustionPolicy.THROW) {
            return index.get() < size;
        }
        return true;
    }
}
