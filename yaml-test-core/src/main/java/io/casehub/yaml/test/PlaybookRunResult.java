package io.casehub.yaml.test;

import java.util.List;
import java.util.Map;

public record PlaybookRunResult(
        boolean success,
        String playbookName,
        String playbookPath,
        long durationMs,
        StepTraceNode trace,
        List<CapturedEvent> events,
        Map<String, Object> output,
        String error) {

    public PlaybookRunResult {
        events = events != null ? List.copyOf(events) : List.of();
        output = output != null ? Map.copyOf(output) : Map.of();
    }
}
