package io.casehub.platform.compiler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record SourceMap(Map<String, SourceLocation> entries) {

    public static SourceMap empty() {
        return new SourceMap(Map.of());
    }

    public Optional<SourceLocation> locate(String taskName) {
        return Optional.ofNullable(entries.get(taskName));
    }

    public record SourceLocation(String yamlPath, int stepIndex) {}

    static final class Builder {
        private final Map<String, SourceLocation> entries = new LinkedHashMap<>();

        void record(String taskName, String yamlPath, int stepIndex) {
            entries.put(taskName, new SourceLocation(yamlPath, stepIndex));
        }

        SourceMap build() {
            return new SourceMap(Map.copyOf(entries));
        }
    }
}
