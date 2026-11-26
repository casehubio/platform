package io.casehub.template.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record TemplateNamespace(
    String name,
    Map<String, TemplateDefinition> templates,
    String language
) {
    public TemplateNamespace(String name,
                             Map<String, TemplateDefinition> templates) {
        this(name, templates, "en");
    }

    public TemplateNamespace {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(templates, "templates");
        language = language != null ? language : "en";
        if (templates.isEmpty()) {
            throw new IllegalArgumentException(
                "Namespace must have at least one template");
        }
        for (var entry : templates.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().name())) {
                throw new IllegalArgumentException(
                    "Map key '" + entry.getKey()
                    + "' must equal definition name '"
                    + entry.getValue().name() + "'");
            }
        }
        templates = Collections.unmodifiableMap(
            new LinkedHashMap<>(templates));
    }
}
