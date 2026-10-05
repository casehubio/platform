package io.casehub.yaml.step.statemachine;

import java.util.LinkedHashMap;

public record StateMachineDefinition(String name, LinkedHashMap<String, StateDefinition> states) {

    public StateMachineDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
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
