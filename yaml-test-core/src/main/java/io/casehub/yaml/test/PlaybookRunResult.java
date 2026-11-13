package io.casehub.yaml.test;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;

public record PlaybookRunResult(
        boolean success,
        String playbookName,
        String playbookPath,
        long durationMs,
        StepTraceNode trace,
        List<CapturedEvent> events,
        Map<String, Object> output,
        Map<String, Map<String, Object>> captures,
        String error) {

    public PlaybookRunResult {
        events   = events != null ? List.copyOf(events) : List.of();
        output   = output != null ? Collections.unmodifiableMap(new LinkedHashMap<>(output)) : Map.of();
        output.keySet().forEach(Objects::requireNonNull);
        captures = captures != null ? Map.copyOf(captures) : Map.of();
    }

    public PlaybookRunResult(boolean success, String playbookName, String playbookPath,
                             long durationMs, StepTraceNode trace, List<CapturedEvent> events,
                             Map<String, Object> output, String error) {
        this(success, playbookName, playbookPath, durationMs, trace, events, output, Map.of(), error);
    }
}
