package io.casehub.platform.simulation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class MapSimulationConfig implements SimulationConfig {

    private final Map<String, String> strategies;
    private final Map<String, Boolean> captures;
    private final Map<String, ExhaustionPolicy> exhaustionPolicies;
    private final Map<String, Double> thresholds;

    private MapSimulationConfig(final Map<String, String> strategies,
                                 final Map<String, Boolean> captures,
                                 final Map<String, ExhaustionPolicy> exhaustionPolicies,
                                 final Map<String, Double> thresholds) {
        this.strategies = Map.copyOf(strategies);
        this.captures = Map.copyOf(captures);
        this.exhaustionPolicies = Map.copyOf(exhaustionPolicies);
        this.thresholds = Map.copyOf(thresholds);
    }

    public static MapSimulationConfig of(final Map<String, String> strategies) {
        return new MapSimulationConfig(strategies, Map.of(), Map.of(), Map.of());
    }

    public static MapSimulationConfig of(final Map<String, String> strategies,
                                          final Map<String, Boolean> captures) {
        return new MapSimulationConfig(strategies, captures, Map.of(), Map.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Optional<String> strategyFor(final String qualifiedName) {
        return Optional.ofNullable(strategies.get(qualifiedName));
    }

    @Override
    public boolean captureEnabled(final String qualifiedName) {
        return captures.getOrDefault(qualifiedName, false);
    }

    @Override
    public Optional<ExhaustionPolicy> exhaustionPolicy(final String qualifiedName) {
        return Optional.ofNullable(exhaustionPolicies.get(qualifiedName));
    }

    @Override
    public Optional<Double> threshold(final String qualifiedName) {
        return Optional.ofNullable(thresholds.get(qualifiedName));
    }

    public Map<String, String> strategies() {
        return strategies;
    }

    public static final class Builder {

        private final Map<String, String> strategies = new LinkedHashMap<>();
        private final Map<String, Boolean> captures = new LinkedHashMap<>();
        private final Map<String, ExhaustionPolicy> exhaustionPolicies = new LinkedHashMap<>();
        private final Map<String, Double> thresholds = new LinkedHashMap<>();

        Builder() {}

        public Builder strategy(final String qualifiedName, final String strategyName) {
            strategies.put(qualifiedName, strategyName);
            return this;
        }

        public Builder capture(final String qualifiedName, final boolean enabled) {
            captures.put(qualifiedName, enabled);
            return this;
        }

        public Builder exhaustion(final String qualifiedName, final ExhaustionPolicy policy) {
            exhaustionPolicies.put(qualifiedName, policy);
            return this;
        }

        public Builder threshold(final String qualifiedName, final double value) {
            thresholds.put(qualifiedName, value);
            return this;
        }

        public MapSimulationConfig build() {
            return new MapSimulationConfig(strategies, captures, exhaustionPolicies, thresholds);
        }
    }
}
