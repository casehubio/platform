package io.casehub.yaml.test;

import java.util.List;
import java.util.Map;

public record TestCase(
        String name,
        List<Map<String, Object>> setupSteps,
        List<Map<String, Object>> steps,
        List<Map<String, Object>> teardownSteps,
        Map<String, Object> simulateBlock) {

    public TestCase {
        setupSteps    = setupSteps != null ? List.copyOf(setupSteps) : List.of();
        steps         = steps != null ? List.copyOf(steps) : List.of();
        teardownSteps = teardownSteps != null ? List.copyOf(teardownSteps) : List.of();
    }

    public TestCase(String name, List<Map<String, Object>> setupSteps,
                    List<Map<String, Object>> steps, List<Map<String, Object>> teardownSteps) {
        this(name, setupSteps, steps, teardownSteps, null);
    }
}
