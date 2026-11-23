package io.casehub.yaml.step.runner;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record TemplateMatchEvent(
        String type,
        Map<String, String> fields,
        double score,
        String originalInput
) {
    public TemplateMatchEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(originalInput, "originalInput");
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
}
