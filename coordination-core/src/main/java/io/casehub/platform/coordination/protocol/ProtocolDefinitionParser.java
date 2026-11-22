package io.casehub.platform.coordination.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.yaml.core.resolver.TemplateExtractor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ProtocolDefinitionParser {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private ProtocolDefinitionParser() {}

    public static ProtocolDefinition parse(String name, InputStream yamlInput) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(yamlInput, "yamlInput");

        Map<String, Object> root;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = YAML_MAPPER.readValue(yamlInput, Map.class);
            root = parsed;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse YAML", e);
        }

        if (root == null) {
            throw new IllegalArgumentException("YAML document is empty");
        }

        Object templatesObj = root.get("templates");
        if (templatesObj == null) {
            throw new IllegalArgumentException("Missing required 'templates' section");
        }
        if (!(templatesObj instanceof Map<?, ?> templatesMap)) {
            throw new IllegalArgumentException("'templates' must be a mapping, not " + templatesObj.getClass().getSimpleName());
        }
        if (templatesMap.isEmpty()) {
            throw new IllegalArgumentException("'templates' must not be empty");
        }

        Map<String, String> templates = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : templatesMap.entrySet()) {
            String templateName = String.valueOf(entry.getKey());
            String pattern = String.valueOf(entry.getValue());
            try {
                TemplateExtractor.compile(pattern);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid template pattern for '" + templateName + "': " + e.getMessage(), e);
            }
            templates.put(templateName, pattern);
        }

        return new ProtocolDefinition(name, templates);
    }

    public static ProtocolDefinition parse(String name, String yaml) {
        Objects.requireNonNull(yaml, "yaml");
        return parse(name, new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
