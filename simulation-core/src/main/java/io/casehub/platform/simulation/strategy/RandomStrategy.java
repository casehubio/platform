package io.casehub.platform.simulation.strategy;

import io.casehub.platform.simulation.SimulationCorpus;
import io.casehub.platform.simulation.SimulationExhaustedException;
import io.casehub.platform.simulation.SimulationStrategy;

import java.util.Random;
import java.util.function.Supplier;

public final class RandomStrategy<I, O> implements SimulationStrategy<I, O> {

    private final SimulationCorpus<I, O> corpus;
    private final String qualifiedName;
    private final Random random;
    private final Supplier<O> generator;

    public RandomStrategy(final SimulationCorpus<I, O> corpus,
                          final String qualifiedName,
                          final Random random,
                          final Supplier<O> generator) {
        this.corpus = corpus;
        this.qualifiedName = qualifiedName;
        this.random = random;
        this.generator = generator;
    }

    @Override
    public O resolve(final I input) {
        if (generator != null) {
            return generator.get();
        }
        final int size = corpus.size(qualifiedName);
        if (size == 0) {
            throw new SimulationExhaustedException(
                    "Corpus is empty for " + qualifiedName);
        }
        final int i = random.nextInt(size);
        return corpus.lookupByIndex(qualifiedName, i)
                .orElseThrow(() -> new SimulationExhaustedException(
                        "No entry at index " + i + " for " + qualifiedName));
    }

    @Override
    public boolean canResolve(final I input) {
        if (generator != null) {
            return true;
        }
        return corpus.size(qualifiedName) > 0;
    }
}
