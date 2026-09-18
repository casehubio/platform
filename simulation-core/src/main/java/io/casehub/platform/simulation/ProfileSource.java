package io.casehub.platform.simulation;

import java.util.Optional;

@FunctionalInterface
public interface ProfileSource {
    Optional<SimulationProfile> resolve(String name);
}
