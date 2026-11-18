package io.casehub.yaml.step.eval;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CaptureCollector {

    private final ConcurrentHashMap<String, Map<String, Object>> captures = new ConcurrentHashMap<>();

    public void capture(String name, Map<String, Object> output) {
        // Not Map.copyOf: step outputs may legitimately contain null-valued fields.
        captures.put(name, java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(output)));
    }

    public Map<String, Map<String, Object>> all() {
        return Map.copyOf(captures);
    }

    public Map<String, Object> get(String name) {
        return captures.get(name);
    }

    public boolean has(String name) {
        return captures.containsKey(name);
    }
}
