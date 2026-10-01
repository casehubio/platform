package io.casehub.yaml.step.scenario;

import java.util.LinkedHashMap;
import java.util.Map;

public record ScenarioDefinition(String name, LinkedHashMap<String, StateDefinition> states) {

    public ScenarioDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Scenario name must not be blank");
        }
        if (states == null || states.isEmpty()) {
            throw new IllegalArgumentException("Scenario must have at least one state");
        }
        states = new LinkedHashMap<>(states);
    }

    public String initialState() {
        return states.keySet().iterator().next();
    }
}
