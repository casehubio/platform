package io.casehub.platform.simulation.strategy;

import io.casehub.platform.simulation.DataRealism;
import io.casehub.platform.simulation.ExhaustionPolicy;
import io.casehub.platform.simulation.KeyExtractor;
import io.casehub.platform.simulation.MatchPolicy;
import io.casehub.platform.simulation.SimulationCorpus;
import io.casehub.platform.simulation.SimulationKeyNotFoundException;
import io.casehub.platform.simulation.SimulationStrategy;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public final class KeyLookupStrategy<I, O> implements SimulationStrategy<I, O> {

    private static final Logger LOG = Logger.getLogger(KeyLookupStrategy.class.getName());

    private final SimulationCorpus<I, O> corpus;
    private final String qualifiedName;
    private final KeyExtractor<I> keyExtractor;
    private final MatchPolicy matchPolicy;
    private final ExhaustionPolicy exhaustionPolicy;
    private final ConcurrentHashMap<String, AtomicInteger> cursors;

    public KeyLookupStrategy(final SimulationCorpus<I, O> corpus,
                             final String qualifiedName,
                             final KeyExtractor<I> keyExtractor) {
        this(corpus, qualifiedName, keyExtractor, MatchPolicy.FIRST, ExhaustionPolicy.CLAMP);
    }

    public KeyLookupStrategy(final SimulationCorpus<I, O> corpus,
                             final String qualifiedName,
                             final KeyExtractor<I> keyExtractor,
                             final MatchPolicy matchPolicy,
                             final ExhaustionPolicy exhaustionPolicy) {
        this.corpus = corpus;
        this.qualifiedName = qualifiedName;
        this.keyExtractor = keyExtractor;
        this.matchPolicy = matchPolicy;
        this.exhaustionPolicy = exhaustionPolicy;
        this.cursors = matchPolicy == MatchPolicy.SEQUENTIAL
                ? new ConcurrentHashMap<>() : null;
    }

    @Override
    public O resolve(final I input) {
        final String key = keyExtractor.extract(input);
        return switch (matchPolicy) {
            case FIRST -> resolveFirst(key);
            case RANDOM -> resolveRandom(key);
            case SEQUENTIAL -> resolveSequential(key);
        };
    }

    private O resolveFirst(final String key) {
        return corpus.lookupByKey(qualifiedName, key)
                .orElseThrow(() -> new SimulationKeyNotFoundException(key));
    }

    private O resolveRandom(final String key) {
        final List<O> all = corpus.lookupAllByKey(qualifiedName, key);
        if (all.isEmpty()) {
            throw new SimulationKeyNotFoundException(key);
        }
        return all.get(ThreadLocalRandom.current().nextInt(all.size()));
    }

    private O resolveSequential(final String key) {
        final List<O> all = corpus.lookupAllByKey(qualifiedName, key);
        if (all.isEmpty()) {
            throw new SimulationKeyNotFoundException(key);
        }
        final String cursorKey = qualifiedName + ":" + key;
        final int i = cursors.computeIfAbsent(cursorKey, k -> new AtomicInteger(0))
                .getAndIncrement();
        final int size = all.size();
        return switch (exhaustionPolicy) {
            case CLAMP -> {
                final int idx = Math.clamp(i, 0, size - 1);
                if (i >= size) {
                    LOG.info(() -> "CLAMP activated for " + cursorKey
                            + " at index " + i + " (size: " + size + ")");
                }
                yield all.get(idx);
            }
            case WRAP -> all.get(i % size);
            case THROW -> {
                if (i >= size) {
                    throw new SimulationKeyNotFoundException(key);
                }
                yield all.get(i);
            }
        };
    }

    @Override
    public boolean canResolve(final I input) {
        final String key = keyExtractor.extract(input);
        return corpus.lookupByKey(qualifiedName, key).isPresent();
    }

    @Override
    public DataRealism dataRealism() {
        return DataRealism.DOMAIN_PLAUSIBLE;
    }
}
