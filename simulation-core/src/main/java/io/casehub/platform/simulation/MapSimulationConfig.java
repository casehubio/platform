package io.casehub.platform.simulation;

import java.util.Map;
import java.util.Optional;

public final class MapSimulationConfig implements SimulationConfig {

    private final Map<String, String> strategies;
    private final Map<String, Boolean> captures;

    private MapSimulationConfig(final Map<String, String> strategies,
                                 final Map<String, Boolean> captures) {
        this.strategies = Map.copyOf(strategies);
        this.captures = Map.copyOf(captures);
    }

    public static MapSimulationConfig of(final Map<String, String> strategies) {
        return new MapSimulationConfig(strategies, Map.of());
    }

    public static MapSimulationConfig of(final Map<String, String> strategies,
                                          final Map<String, Boolean> captures) {
        return new MapSimulationConfig(strategies, captures);
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
        return Optional.empty();
    }

    public Map<String, String> strategies() {
        return strategies;
    }
}
