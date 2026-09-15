package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.ExhaustionPolicy;

import java.util.Optional;

class MethodSimulationConfig {

    private String strategy;
    private boolean capture;
    private ExhaustionPolicy exhaustionPolicy;
    private String keyExtractor;

    Optional<String> strategy() {
        return Optional.ofNullable(strategy);
    }

    boolean capture() {
        return capture;
    }

    Optional<ExhaustionPolicy> exhaustionPolicy() {
        return Optional.ofNullable(exhaustionPolicy);
    }

    Optional<String> keyExtractor() {
        return Optional.ofNullable(keyExtractor);
    }

    void set(String property, String value) {
        switch (property) {
            case "strategy" -> this.strategy = value;
            case "capture" -> this.capture = Boolean.parseBoolean(value);
            case "exhaustion-policy" -> this.exhaustionPolicy =
                    ExhaustionPolicy.valueOf(value.toUpperCase().replace("-", "_"));
            case "key-extractor" -> this.keyExtractor = value;
            default -> { /* ignore unknown properties */ }
        }
    }
}
