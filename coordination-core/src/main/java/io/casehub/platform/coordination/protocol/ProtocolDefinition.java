package io.casehub.platform.coordination.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ProtocolDefinition(String name, Map<String, String> templates) {

    public ProtocolDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(templates, "templates");
        if (templates.isEmpty()) {
            throw new IllegalArgumentException("Protocol must have at least one template");
        }
        templates = Collections.unmodifiableMap(new LinkedHashMap<>(templates));
    }
}
