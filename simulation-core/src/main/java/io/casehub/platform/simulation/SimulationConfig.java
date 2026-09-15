package io.casehub.platform.simulation;

import java.util.Optional;

public interface SimulationConfig {

    Optional<String> strategyFor(String qualifiedName);

    boolean captureEnabled(String qualifiedName);

    Optional<ExhaustionPolicy> exhaustionPolicy(String qualifiedName);
}
