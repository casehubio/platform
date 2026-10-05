package io.casehub.yaml.step.statemachine;

import java.util.List;
import java.util.Map;

public record StateDefinition(
        String name,
        List<Map<String, Object>> steps,
        String next,
        String onFailure,
        String deadline,
        Map<String, EventTransition> events,
        boolean isTerminal) {

    public StateDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("State name must not be blank");
        }
        steps = steps != null ? List.copyOf(steps) : List.of();
        events = events != null ? Map.copyOf(events) : Map.of();
        if (next != null && !events.isEmpty()) {
            throw new IllegalArgumentException("State '" + name + "': 'next' and 'on' are mutually exclusive");
        }
    }

    public static StateDefinition terminal(String name, List<Map<String, Object>> steps) {
        return new StateDefinition(name, steps, null, null, null, Map.of(), true);
    }

    public static StateDefinition completionDriven(String name, List<Map<String, Object>> steps,
                                                    String next, String onFailure, String deadline) {
        return new StateDefinition(name, steps, next, onFailure, deadline, Map.of(), false);
    }

    public static StateDefinition eventDriven(String name, List<Map<String, Object>> steps,
                                               Map<String, EventTransition> events,
                                               String onFailure, String deadline) {
        return new StateDefinition(name, steps, null, onFailure, deadline, events, false);
    }
}
