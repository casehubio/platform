package io.casehub.platform.simulation;

import io.casehub.platform.simulation.strategy.KeyLookupStrategy;
import io.casehub.platform.simulation.strategy.RandomStrategy;
import io.casehub.platform.simulation.strategy.RecordedReplayStrategy;
import io.casehub.platform.simulation.strategy.SequentialStrategy;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"rawtypes", "unchecked"})
public class SimulationRuntime {

    private final SimulationConfig config;
    private final SimulationCorpus corpus;
    private final ConcurrentHashMap<String, KeyExtractor<?>> extractors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SimulationStrategy<?, ?>> strategyCache = new ConcurrentHashMap<>();

    public SimulationRuntime(final SimulationConfig config, final SimulationCorpus corpus) {
        this.config = config;
        this.corpus = corpus;
    }

    public <I> void registerExtractor(final String qualifiedName, final KeyExtractor<I> extractor) {
        extractors.put(qualifiedName, extractor);
    }

    public <I, O> Optional<SimulationStrategy<I, O>> strategyFor(final String qualifiedName) {
        return config.strategyFor(qualifiedName)
                .map(strategyName -> (SimulationStrategy<I, O>) strategyCache.computeIfAbsent(
                        qualifiedName, qn -> createStrategy(qn, strategyName)));
    }

    public boolean captureEnabled(final String qualifiedName) {
        return config.captureEnabled(qualifiedName);
    }

    public <I, O> void capture(final String qualifiedName, final String tenancyId,
                               final I input, final O output) {
        corpus.record(qualifiedName, tenancyId, input, output);
    }

    public <I, O> void capture(final String qualifiedName, final String tenancyId,
                               final String key, final I input, final O output) {
        corpus.record(qualifiedName, tenancyId, key, input, output);
    }

    private SimulationStrategy<?, ?> createStrategy(final String qualifiedName, final String strategyName) {
        return switch (strategyName) {
            case "sequential" -> new SequentialStrategy<>(corpus, qualifiedName,
                    config.exhaustionPolicy(qualifiedName).orElse(ExhaustionPolicy.WRAP));
            case "key-lookup" -> {
                final KeyExtractor extractor = requireExtractor(qualifiedName);
                yield new KeyLookupStrategy<>(corpus, qualifiedName, extractor);
            }
            case "random" -> new RandomStrategy<>(corpus, qualifiedName, new Random(), null);
            case "recorded-replay" -> {
                final KeyExtractor extractor = requireExtractor(qualifiedName);
                yield new RecordedReplayStrategy<>(corpus, qualifiedName, extractor);
            }
            default -> throw new SimulationConfigException(
                    "Unknown strategy '" + strategyName + "' for " + qualifiedName);
        };
    }

    private KeyExtractor<?> requireExtractor(final String qualifiedName) {
        final KeyExtractor<?> extractor = extractors.get(qualifiedName);
        if (extractor == null) {
            throw new SimulationConfigException(
                    "Strategy for " + qualifiedName + " requires a KeyExtractor, but none registered");
        }
        return extractor;
    }
}
