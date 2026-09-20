package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.TemporalProfile;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class TemporalProfileRegistry {

    private final Map<String, TemporalProfile<Map<String, Object>>> profiles;

    public TemporalProfileRegistry(Map<String, TemporalProfile<Map<String, Object>>> profiles) {
        this.profiles = Map.copyOf(profiles);
    }

    public Optional<TemporalProfile<Map<String, Object>>> resolve(String name) {
        return Optional.ofNullable(profiles.get(name));
    }

    public Set<String> profileNames() {
        return Collections.unmodifiableSet(profiles.keySet());
    }
}
