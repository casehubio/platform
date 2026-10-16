package io.casehub.platform.simulation.strategy;

import io.casehub.platform.simulation.DataRealism;
import io.casehub.platform.simulation.ExhaustionPolicy;
import io.casehub.platform.simulation.SimulationCorpus;
import io.casehub.platform.simulation.SimulationExhaustedException;
import io.casehub.platform.simulation.SimulationStrategy;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public final class SequentialStrategy<I, O> implements SimulationStrategy<I, O> {

    private static final Logger LOG = Logger.getLogger(SequentialStrategy.class.getName());

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
        final int resolvedIndex = switch (exhaustionPolicy) {
            case THROW -> {
                if (i >= size) {
                    throw new SimulationExhaustedException(
                            "Corpus exhausted at index " + i + " (size: " + size + ") for " + qualifiedName);
                }
                yield i;
            }
            case WRAP -> i % size;
            case CLAMP -> {
                final int idx = Math.clamp(i, 0, size - 1);
                if (i >= size) {
                    LOG.info(() -> "CLAMP activated for " + qualifiedName
                            + " at index " + i + " (size: " + size + ")");
                }
                yield idx;
            }
        };
        return corpus.lookupByIndex(qualifiedName, resolvedIndex)
                .orElseThrow(() -> new SimulationExhaustedException(
                        "No entry at index " + resolvedIndex + " for " + qualifiedName));
    }

    @Override
    public boolean canResolve(final I input) {
        final int size = corpus.size(qualifiedName);
        if (size == 0) {
            return false;
        }
        return switch (exhaustionPolicy) {
            case THROW -> index.get() < size;
            case WRAP, CLAMP -> true;
        };
    }

    @Override
    public DataRealism dataRealism() {
        return DataRealism.STRUCTURALLY_VALID;
    }
}
