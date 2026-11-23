package io.casehub.extraction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record MessageType(String name, List<String> templates, Map<String, VariableConstraint> variables) {
    public MessageType {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(templates, "templates");
        if (templates.isEmpty()) throw new IllegalArgumentException("Message type must have at least one template");
        templates = List.copyOf(templates);
        variables = variables != null ? Collections.unmodifiableMap(new LinkedHashMap<>(variables)) : Map.of();
    }
}
