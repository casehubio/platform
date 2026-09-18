package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.ExhaustionPolicy;
import io.casehub.platform.simulation.SimulationConfig;
import org.eclipse.microprofile.config.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SmallRyeSimulationConfig implements SimulationConfig {

    private static final String PREFIX = "casehub.simulation.";
    private final Map<String, MethodSimulationConfig> methods;

    public SmallRyeSimulationConfig(Config config) {
        this.methods = new HashMap<>();
        for (String name : config.getPropertyNames()) {
            if (!name.startsWith(PREFIX)) {
                continue;
            }
            String suffix = name.substring(PREFIX.length());
            String[] parts = suffix.split("\\.");
            if (parts.length != 3) {
                continue;
            }
            String qualifiedName = parts[0] + "." + parts[1];
            String property = parts[2];
            config.getOptionalValue(name, String.class)
                    .ifPresent(value -> methods
                            .computeIfAbsent(qualifiedName, k -> new MethodSimulationConfig())
                            .set(property, value));
        }
    }

    @Override
    public Optional<String> strategyFor(String qualifiedName) {
        return Optional.ofNullable(methods.get(qualifiedName))
                .flatMap(MethodSimulationConfig::strategy);
    }

    @Override
    public boolean captureEnabled(String qualifiedName) {
        return Optional.ofNullable(methods.get(qualifiedName))
                .map(MethodSimulationConfig::capture)
                .orElse(false);
    }

    @Override
    public Optional<ExhaustionPolicy> exhaustionPolicy(String qualifiedName) {
        return Optional.ofNullable(methods.get(qualifiedName))
                .flatMap(MethodSimulationConfig::exhaustionPolicy);
    }

    public Map<String, String> extractorSpecs() {
        return methods.entrySet().stream()
                .filter(e -> e.getValue().keyExtractor().isPresent())
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().keyExtractor().orElseThrow()));
    }
}
