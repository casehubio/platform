package io.casehub.yaml.step.statemachine;

import io.casehub.yaml.core.error.LocationIndex;
import io.casehub.yaml.core.error.SourceLocation;
import io.casehub.yaml.core.playbook.PlaybookDocument;
import io.casehub.yaml.core.playbook.PlaybookFrontMatter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlaybookParser {

    private static final String PLAYBOOK_KEY = "playbook";
    private static final String SCHEMA_KEY = "schema";
    private static final String NAME_KEY = "name";
    private static final String STEPS_KEY = "steps";

    public record ParseResult(PlaybookDocument document, LocationIndex locations) {}

    public record RawParseResult(Object root, LocationIndex locations) {}

    public static RawParseResult parseRawWithLocations(String yaml, String filePath) {
        if (yaml == null || yaml.isBlank()) {
            return new RawParseResult(null, new LocationIndex());
        }
        var    index  = new LocationIndex();
        var    loader = new org.yaml.snakeyaml.Yaml();
        Object root   = null;
        for (var node : loader.composeAll(new java.io.StringReader(yaml))) {
            root = convertNode(node, index, filePath);
            break;
        }
        return new RawParseResult(root, index);
    }


    private PlaybookParser() {}

    public static PlaybookDocument parse(String yaml) {
        return parseWithLocations(yaml, null).document();
    }

    @SuppressWarnings("unchecked")
    public static ParseResult parseWithLocations(String yaml, String filePath) {
        if (yaml == null || yaml.isBlank()) {
            throw new IllegalArgumentException("YAML content must not be empty");
        }

        var index  = new LocationIndex();
        var loader = new org.yaml.snakeyaml.Yaml();
        var nodes  = new ArrayList<org.yaml.snakeyaml.nodes.Node>();
        for (var node : loader.composeAll(new java.io.StringReader(yaml))) {
            nodes.add(node);
        }

        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("YAML content must not be empty");
        }

        var documents = new ArrayList<Object>(nodes.size());
        for (var node : nodes) {
            documents.add(convertNode(node, index, filePath));
        }

        if (documents.get(0) == null) {
            throw new IllegalArgumentException("YAML content must not be empty");
        }

        PlaybookDocument doc = assembleDocument(documents);
        return new ParseResult(doc, index);
    }

    @SuppressWarnings("unchecked")
    private static PlaybookDocument assembleDocument(List<Object> documents) {
        if (documents.size() == 1) {
            var root = asMap(documents.get(0));
            return new PlaybookDocument(null, root);
        }

        var firstDoc = asMap(documents.get(0));

        if (!firstDoc.containsKey(PLAYBOOK_KEY)) {
            var merged    = new LinkedHashMap<String, Object>(firstDoc);
            var secondDoc = asMap(documents.get(1));
            merged.putAll(secondDoc);
            return new PlaybookDocument(null, merged);
        }

        var frontMatter = extractFrontMatter(firstDoc);

        if (documents.size() >= 3) {
            var metadata = new LinkedHashMap<>(asMap(documents.get(1)));
            rejectStepsWrapper(metadata);

            String playbookName = null;
            if (metadata.containsKey(PLAYBOOK_KEY)) {
                playbookName = String.valueOf(metadata.remove(PLAYBOOK_KEY));
            }
            if (frontMatter.name() == null && playbookName != null) {
                frontMatter = new PlaybookFrontMatter(
                        frontMatter.version(), frontMatter.schema(),
                        playbookName, frontMatter.metadata());
            }

            Object thirdDoc = documents.get(2);
            if (thirdDoc instanceof List<?> stepList) {
                metadata.put("steps", stepList);
            } else if (thirdDoc instanceof Map<?, ?> mapDoc) {
                var typedMap = (Map<String, Object>) mapDoc;
                rejectStepsWrapper(typedMap);
                metadata.putAll(typedMap);
            }

            return new PlaybookDocument(frontMatter, metadata);
        }

        Object secondDoc = documents.get(1);
        if (secondDoc instanceof List<?> stepList) {
            return new PlaybookDocument(frontMatter, Map.of(STEPS_KEY, stepList));
        }
        var content = asMap(secondDoc);
        rejectStepsWrapper(content);
        return new PlaybookDocument(frontMatter, content);
    }

    private static void rejectStepsWrapper(Map<String, Object> content) {
        if (content.containsKey(STEPS_KEY) && content.get(STEPS_KEY) instanceof List<?>) {
            throw new IllegalArgumentException(
                    "The 'steps:' wrapper key is not allowed in versioned playbooks. "
                    + "Use bare-list format: add '---' before the step list and remove the 'steps:' line.");
        }
    }

    private static Object convertNode(org.yaml.snakeyaml.nodes.Node node,
                                      LocationIndex index, String filePath) {
        if (node instanceof org.yaml.snakeyaml.nodes.MappingNode mapping) {
            var map  = new LinkedHashMap<String, Object>();
            var mark = mapping.getStartMark();
            if (mark != null) {
                index.put(map, new SourceLocation(filePath, mark.getLine() + 1, mark.getColumn() + 1));
            }
            for (var tuple : mapping.getValue()) {
                String key = ((org.yaml.snakeyaml.nodes.ScalarNode) tuple.getKeyNode()).getValue();
                map.put(key, convertNode(tuple.getValueNode(), index, filePath));
            }
            return map;
        }
        if (node instanceof org.yaml.snakeyaml.nodes.SequenceNode sequence) {
            var list = new ArrayList<Object>();
            var mark = sequence.getStartMark();
            if (mark != null) {
                index.put(list, new SourceLocation(filePath, mark.getLine() + 1, mark.getColumn() + 1));
            }
            for (var item : sequence.getValue()) {
                list.add(convertNode(item, index, filePath));
            }
            return list;
        }
        if (node instanceof org.yaml.snakeyaml.nodes.ScalarNode scalar) {
            return resolveScalar(scalar);
        }
        throw new IllegalArgumentException("Unknown YAML node type: " + node.getClass().getSimpleName());
    }

    private static Object resolveScalar(org.yaml.snakeyaml.nodes.ScalarNode scalar) {
        String value = scalar.getValue();
        if (!scalar.isPlain()) {
            return value;
        }
        if (value.isEmpty() || "null".equals(value) || "~".equals(value)) {
            return null;
        }
        if ("true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value)
            || "on".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value)
            || "off".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {}
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {}
        try {
            if (value.contains(".") || value.contains("e") || value.contains("E")) {
                return Double.parseDouble(value);
            }
        } catch (NumberFormatException ignored) {}
        return value;
    }

    private static PlaybookFrontMatter extractFrontMatter(Map<String, Object> doc) {
        String version = String.valueOf(doc.get(PLAYBOOK_KEY));
        String schema  = (String) doc.get(SCHEMA_KEY);
        if (schema == null) {
            throw new IllegalArgumentException("Playbook front matter requires a 'schema' field");
        }
        String name = (String) doc.get(NAME_KEY);

        var metadata = new LinkedHashMap<String, Object>();
        for (var entry : doc.entrySet()) {
            if (!PLAYBOOK_KEY.equals(entry.getKey())
                && !SCHEMA_KEY.equals(entry.getKey())
                && !NAME_KEY.equals(entry.getKey())) {
                metadata.put(entry.getKey(), entry.getValue());
            }
        }

        return new PlaybookFrontMatter(version, schema, name,
                                       metadata.isEmpty() ? Map.of() : metadata);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        throw new IllegalArgumentException("YAML document must be a mapping, got: "
                                           + (obj != null ? obj.getClass().getSimpleName() : "null"));
    }
}
