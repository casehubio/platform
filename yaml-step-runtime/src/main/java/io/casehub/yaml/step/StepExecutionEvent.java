package io.casehub.yaml.step;

import java.util.Map;

public record StepExecutionEvent(
        String actionName,
        long durationMs,
        boolean success,
        Map<String, Object> metadata) {}
