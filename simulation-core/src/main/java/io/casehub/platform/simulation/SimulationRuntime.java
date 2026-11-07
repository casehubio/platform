package io.casehub.platform.simulation;

import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import io.casehub.platform.simulation.strategy.KeyLookupStrategy;
import io.casehub.platform.simulation.strategy.NearestMatchStrategy;
import io.casehub.platform.simulation.strategy.RandomStrategy;
import io.casehub.platform.simulation.strategy.RecordedReplayStrategy;
import io.casehub.platform.simulation.strategy.SequentialStrategy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings({"rawtypes", "unchecked"})
public class SimulationRuntime {

    private final SimulationConfig config;
    private final SimulationCorpus corpus;
    private final ConcurrentHashMap<String, KeyExtractor<?>> extractors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SimilarityScorer<?>> scorers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SimulationStrategy<?, ?>> strategyCache = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<SimulationOverlay> overlayStack = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, CorpusSelector> corpusSelectors = new ConcurrentHashMap<>();
    private       ProfileSource                           profileSource;
    private volatile double                               globalSpeed;


    public SimulationRuntime(final SimulationConfig config, final SimulationCorpus corpus) {
        this.config = config;
        this.corpus = corpus;
        this.globalSpeed = config.speed();
    }

    public <I> void registerExtractor(final String qualifiedName, final KeyExtractor<I> extractor) {
        extractors.put(qualifiedName, extractor);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void apply(final CorpusSeed<?, ?> seed) {
        ((CorpusSeed) seed).seedInto(corpus);
        if (seed.keyExtractor() != null) {
            registerExtractor(seed.qualifiedName(), seed.keyExtractor());
        }
    }

    public <I> void registerScorer(final String qualifiedName, final SimilarityScorer<I> scorer) {
        scorers.put(qualifiedName, scorer);
    }

    public <I, O> Optional<SimulationStrategy<I, O>> strategyFor(final String qualifiedName) {
        var stack = overlayStack;
        for (int i = stack.size() - 1; i >= 0; i--) {
            var overlay         = stack.get(i);
            var overlayStrategy = overlay.config().strategyFor(qualifiedName);
            if (overlayStrategy.isPresent()) {
                return Optional.of((SimulationStrategy<I, O>) overlay.strategyCache()
                                                                     .computeIfAbsent(qualifiedName, qn ->
                                                                                                             createStrategy(qn, overlayStrategy.get(), overlay.corpus(), overlay.config())));
            }
        }
        return config.strategyFor(qualifiedName)
                     .map(strategyName -> (SimulationStrategy<I, O>) strategyCache.computeIfAbsent(
                             qualifiedName, qn -> createStrategy(qn, strategyName, corpus, config)));
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    public SimulationOverlay pushOverlay(final SimulationConfig config, final SimulationCorpus corpus) {
        final var overlay = new SimulationOverlay(config, corpus);
        overlayStack.add(overlay);
        strategyCache.clear();
        for (String qn : config.corpusSelectionKeys()) {
            config.corpusSelection(qn).ifPresent(sel -> buildSelector(qn, sel));
        }
        return overlay;
    }

    public SimulationOverlay pushOverlay(final SimulationConfig config) {
        return pushOverlay(config, new InMemorySimulationCorpus<>());
    }

    public void popOverlay(final SimulationOverlay overlay) {
        if (!overlayStack.remove(overlay)) {
            throw new IllegalArgumentException("Overlay not found in stack");
        }
        strategyCache.clear();
    }

    public void popAll() {
        overlayStack.clear();
        strategyCache.clear();
    }

    public List<JournalEntry> journal(final SimulationOverlay overlay) {
        return overlay.journal().entries();
    }

    public boolean hasActiveOverlay() {
        return !overlayStack.isEmpty();
    }

    public double globalSpeed() {
        return globalSpeed;
    }

    public void setGlobalSpeed(double speed) {
        if (speed <= 0) {throw new IllegalArgumentException("speed must be positive");}
        this.globalSpeed = speed;
    }


    public void setProfileSource(final ProfileSource profileSource) {
        this.profileSource = profileSource;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public SimulationOverlay pushProfile(final String name) {
        if (profileSource == null) {
            throw new SimulationConfigException(
                    "No ProfileSource registered — cannot resolve profile '" + name + "'");
        }
        final SimulationProfile profile = profileSource.resolve(name)
                                                       .orElseThrow(() -> new SimulationConfigException(
                                                               "Unknown simulation profile: '" + name + "'"));
        return pushOverlay(profile.config(), (SimulationCorpus) profile.corpus());
    }


    public void recordJournal(final String qualifiedName, final String tenancyId,
                               final Object input, final Object output, final DataRealism dataRealism) {
        final var stack = overlayStack;
        if (stack.isEmpty()) return;
        final var topOverlay = stack.get(stack.size() - 1);
        topOverlay.journal().record(new JournalEntry(qualifiedName, tenancyId, input, output,
                Instant.now(), dataRealism));
    }

    public DataRealism fallthroughRealism(final String qualifiedName) {
        final var stack = overlayStack;
        for (int i = stack.size() - 1; i >= 0; i--) {
            final DataRealism level = stack.get(i).config().fallthroughRealism(qualifiedName);
            if (level != null) {return level;}
        }
        return config.fallthroughRealism(qualifiedName);
    }


    static String resolveAlias(final String name) {
        return switch (name) {
            case "seq" -> "sequential";
            case "key" -> "key-lookup";
            case "rand" -> "random";
            case "replay" -> "recorded-replay";
            case "nearest" -> "nearest-match";
            default -> name;
        };
    }

    private SimulationStrategy<?, ?> createStrategy(final String qualifiedName, final String strategyName,
                                                      final SimulationCorpus corpus, final SimulationConfig effectiveConfig) {
        SimulationCorpus effectiveCorpus = corpus;
        CorpusSelector selector = null;
        Optional<CorpusSelectorConfig> selection = effectiveConfig.corpusSelection(qualifiedName);
        if (selection.isPresent()) {
            selector = buildSelector(qualifiedName, selection.get());
            effectiveCorpus = new PhasedCorpus<>(selector, selection.get().phases());
        }

        SimulationStrategy<?, ?> strategy = switch (resolveAlias(strategyName)) {
            case "sequential" -> new SequentialStrategy<>(effectiveCorpus, qualifiedName,
                    effectiveConfig.exhaustionPolicy(qualifiedName).orElse(ExhaustionPolicy.WRAP));
            case "key-lookup" -> {
                final KeyExtractor extractor = requireExtractor(qualifiedName);
                final MatchPolicy mp = effectiveConfig.matchPolicy(qualifiedName).orElse(MatchPolicy.FIRST);
                final ExhaustionPolicy ep = effectiveConfig.exhaustionPolicy(qualifiedName)
                        .orElse(ExhaustionPolicy.CLAMP);
                yield new KeyLookupStrategy<>(effectiveCorpus, qualifiedName, extractor, mp, ep);
            }
            case "random" -> new RandomStrategy<>(effectiveCorpus, qualifiedName, new Random(), null);
            case "recorded-replay" -> {
                final KeyExtractor extractor = requireExtractor(qualifiedName);
                yield new RecordedReplayStrategy<>(effectiveCorpus, qualifiedName, extractor);
            }
            case "nearest-match" -> {
                final SimilarityScorer scorer = requireScorer(qualifiedName);
                final double threshold = effectiveConfig.threshold(qualifiedName).orElse(0.0);
                yield new NearestMatchStrategy<>(effectiveCorpus, qualifiedName, scorer, threshold);
            }
            default -> throw new SimulationConfigException(
                    "Unknown strategy '" + strategyName + "' for " + qualifiedName);
        };

        if (selector != null) {
            strategy = wrapWithSelector(strategy, selector);
        }
        return strategy;
    }

    private CorpusSelector buildSelector(final String qualifiedName, final CorpusSelectorConfig selectorConfig) {
        return corpusSelectors.computeIfAbsent(qualifiedName, qn -> switch (selectorConfig) {
            case CorpusSelectorConfig.StateMachine sm ->
                    new io.casehub.platform.simulation.selector.StateMachineSelector(sm.initial(), sm.transitions());
            case CorpusSelectorConfig.DurationBased d ->
                    new io.casehub.platform.simulation.selector.DurationSelector(d.orderedPhases(), this::globalSpeed);
            case CorpusSelectorConfig.LookupCount lc ->
                    new io.casehub.platform.simulation.selector.LookupCountSelector(lc.orderedPhases());
            case CorpusSelectorConfig.Weighted w ->
                    new io.casehub.platform.simulation.selector.RandomSelector(w.weightedPhases(), new Random());
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static SimulationStrategy<?, ?> wrapWithSelector(final SimulationStrategy strategy,
                                                              final CorpusSelector selector) {
        return new SimulationStrategy<>() {
            @Override public Object resolve(Object input) {
                Object result = strategy.resolve(input);
                selector.onResolved();
                return result;
            }
            @Override public boolean canResolve(Object input) {
                return strategy.canResolve(input);
            }
            @Override public DataRealism dataRealism() {
                return strategy.dataRealism();
            }
        };
    }

    public Optional<io.casehub.platform.simulation.selector.StateMachineSelector> stateMachineSelector(final String qualifiedName) {
        CorpusSelector selector = corpusSelectors.get(qualifiedName);
        return selector instanceof io.casehub.platform.simulation.selector.StateMachineSelector sms
                ? Optional.of(sms) : Optional.empty();
    }

    public void advanceStateMachines(String trigger) {
        for (var entry : corpusSelectors.entrySet()) {
            if (entry.getValue() instanceof io.casehub.platform.simulation.selector.StateMachineSelector sms) {
                sms.advance(trigger);
            }
        }
    }

    public java.util.Map<String, String> stateMachineStates() {
        var states = new java.util.LinkedHashMap<String, String>();
        for (var entry : corpusSelectors.entrySet()) {
            if (entry.getValue() instanceof io.casehub.platform.simulation.selector.StateMachineSelector sms) {
                states.put(entry.getKey(), sms.activePhase());
            }
        }
        return states;
    }


    private SimilarityScorer<?> requireScorer(final String qualifiedName) {
        final SimilarityScorer<?> scorer = scorers.get(qualifiedName);
        if (scorer == null) {
            throw new SimulationConfigException(
                    "Strategy for " + qualifiedName + " requires a SimilarityScorer, but none registered");
        }
        return scorer;
    }

    private static final KeyExtractor<Object> IDENTITY_EXTRACTOR = String::valueOf;

    private KeyExtractor<?> requireExtractor(final String qualifiedName) {
        return extractors.getOrDefault(qualifiedName, IDENTITY_EXTRACTOR);
    }
}
