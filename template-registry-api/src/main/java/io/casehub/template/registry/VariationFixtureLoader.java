package io.casehub.template.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VariationFixtureLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private VariationFixtureLoader() {}

    public static List<VariationFixture> load(Path file) {
        try (InputStream is = Files.newInputStream(file)) {
            return load(is);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load fixture file: " + file, e);
        }
    }

    public static List<VariationFixture> load(InputStream is) {
        try {
            JsonNode root = YAML.readTree(is);
            JsonNode fixturesNode = root.get("fixtures");
            if (fixturesNode == null || !fixturesNode.isArray()) {
                return List.of();
            }

            List<VariationFixture> fixtures = new ArrayList<>();
            for (JsonNode entry : fixturesNode) {
                String template = entry.get("template").asText();
                String input = entry.get("input").asText();
                boolean expectMatch = entry.has("expectMatch") && entry.get("expectMatch").asBoolean();

                Map<String, String> expectedFields = new LinkedHashMap<>();
                JsonNode fieldsNode = entry.get("expectedFields");
                if (fieldsNode != null && fieldsNode.isObject()) {
                    var it = fieldsNode.fields();
                    while (it.hasNext()) {
                        var field = it.next();
                        expectedFields.put(field.getKey(), field.getValue().asText());
                    }
                }

                fixtures.add(new VariationFixture(template, input, expectMatch, expectedFields));
            }
            return List.copyOf(fixtures);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse fixture YAML", e);
        }
    }

    public static String namespace(Path file) {
        try (InputStream is = Files.newInputStream(file)) {
            JsonNode root = YAML.readTree(is);
            JsonNode ns = root.get("namespace");
            if (ns == null || ns.isNull()) {
                throw new IllegalArgumentException("Fixture file missing 'namespace' field: " + file);
            }
            return ns.asText();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read namespace from: " + file, e);
        }
    }
}
