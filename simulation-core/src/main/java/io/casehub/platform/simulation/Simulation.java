package io.casehub.platform.simulation;

import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class Simulation {

    private final SimulationRuntime runtime;
    private final SimulationConfig config;
    private final SimulationCorpus corpus;
    private SimulationOverlay currentOverlay;

    Simulation(SimulationRuntime runtime, SimulationConfig config,
               SimulationCorpus corpus) {
        this.runtime = runtime;
        this.config = config;
        this.corpus = corpus;
    }

    public static Builder forTest() {
        return new Builder("test");
    }

    public static Builder forTest(String defaultTenancyId) {
        return new Builder(defaultTenancyId);
    }

    public <I, O> O resolve(String qualifiedName, I input) {
        SimulationStrategy<I, O> strategy = runtime
                .<I, O>strategyFor(qualifiedName)
                .orElseThrow(() -> new SimulationConfigException(
                        "No strategy configured for '" + qualifiedName + "'"));
        O output = strategy.resolve(input);
        runtime.recordJournal(qualifiedName, null, input, output, true);
        return output;
    }

    public SimulationRuntime runtime() {
        return runtime;
    }

    public SimulationOverlay overlay() {
        currentOverlay = runtime.pushOverlay(config, corpus);
        return currentOverlay;
    }

    public void popOverlay(SimulationOverlay overlay) {
        runtime.popOverlay(overlay);
        if (overlay == currentOverlay) {
            currentOverlay = null;
        }
    }

    public SimulationVerifier verifier() {
        if (currentOverlay == null) {
            throw new IllegalStateException(
                    "No active overlay — call overlay() first");
        }
        return SimulationVerifier.on(currentOverlay.journal());
    }

    public static final class Builder {

        private final String defaultTenancyId;
        private final Map<String, String> strategies = new LinkedHashMap<>();
        private final Map<String, List<InvocationRecord>> records = new LinkedHashMap<>();
        private final Map<String, KeyExtractor<?>> extractors = new LinkedHashMap<>();

        Builder(String defaultTenancyId) {
            this.defaultTenancyId = defaultTenancyId;
        }

        public <I, O> Builder stub(String qualifiedName, I input, O output) {
            String key = String.valueOf(input);
            addRecord(qualifiedName, key, input, output);
            mergeStrategy(qualifiedName, "key-lookup");
            extractors.putIfAbsent(qualifiedName,
                    (KeyExtractor<Object>) i -> String.valueOf(i));
            return this;
        }

        public <I, O> Builder stub(String qualifiedName,
                                    String key, I input, O output) {
            addRecord(qualifiedName, key, input, output);
            mergeStrategy(qualifiedName, "key-lookup");
            return this;
        }

        public <I, O> Builder seed(String qualifiedName, I input, O output) {
            addRecord(qualifiedName, null, input, output);
            mergeStrategy(qualifiedName, "sequential");
            return this;
        }

        public <I> Builder keyExtractor(String qualifiedName,
                                         KeyExtractor<I> extractor) {
            extractors.put(qualifiedName, extractor);
            return this;
        }

        public Builder strategy(String qualifiedName, String strategyName) {
            strategies.put(qualifiedName, strategyName);
            return this;
        }

        public Simulation build() {
            var corpus = new InMemorySimulationCorpus();
            records.forEach(corpus::seed);

            var config = MapSimulationConfig.of(strategies);
            var runtime = new SimulationRuntime(config, corpus);
            extractors.forEach(runtime::registerExtractor);

            return new Simulation(runtime, config, corpus);
        }

        private void addRecord(String qualifiedName, String key,
                                Object input, Object output) {
            records.computeIfAbsent(qualifiedName, k -> new ArrayList<>())
                    .add(InvocationRecord.of(defaultTenancyId, key,
                            input, output));
        }

        private void mergeStrategy(String qualifiedName, String strategyName) {
            strategies.merge(qualifiedName, strategyName, (existing, incoming) -> {
                if (!existing.equals(incoming)) {
                    throw new SimulationConfigException(
                            "Ambiguous strategy for '" + qualifiedName + "': "
                            + "stub() implies key-lookup but seed() implies sequential. "
                            + "Use one or the other, or call .strategy() explicitly.");
                }
                return existing;
            });
        }
    }
}
