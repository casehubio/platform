package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.ExhaustionPolicy;

import java.util.Optional;

class MethodSimulationConfig {

    private String strategy;
    private boolean capture;
    private ExhaustionPolicy exhaustionPolicy;
    private String keyExtractor;
    private String scorer;
    private Double threshold;

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

    Optional<String> scorer() {
        return Optional.ofNullable(scorer);
    }

    Optional<Double> threshold() {
        return Optional.ofNullable(threshold);
    }

    void set(String property, String value) {
        switch (property) {
            case "strategy" -> this.strategy = value;
            case "capture" -> this.capture = Boolean.parseBoolean(value);
            case "exhaustion-policy" -> this.exhaustionPolicy =
                    ExhaustionPolicy.valueOf(value.toUpperCase().replace("-", "_"));
            case "key-extractor" -> this.keyExtractor = value;
            case "scorer" -> this.scorer = value;
            case "threshold" -> this.threshold = Double.parseDouble(value);
            default -> { /* ignore unknown properties */ }
        }
    }
}
