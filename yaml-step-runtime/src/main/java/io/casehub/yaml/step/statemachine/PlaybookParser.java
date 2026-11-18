package io.casehub.yaml.step.statemachine;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.yaml.core.error.LocationIndex;
import io.casehub.yaml.core.playbook.PlaybookDocument;
import io.casehub.yaml.core.playbook.PlaybookFrontMatter;
import io.casehub.yaml.jackson.LocationTrackingModule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlaybookParser {

    private static final String PLAYBOOK_KEY = "playbook";
    private static final String SCHEMA_KEY = "schema";
    private static final String NAME_KEY = "name";
    private static final String STEPS_KEY = "steps";

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
            .registerModule(new LocationTrackingModule())
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);

    public record ParseResult(PlaybookDocument document, LocationIndex locations) {}

    public record RawParseResult(Object root, LocationIndex locations) {}

    public static RawParseResult parseRawWithLocations(String yaml, String filePath) {
        if (yaml == null || yaml.isBlank()) {
            return new RawParseResult(null, new LocationIndex());
        }
        var index = new LocationIndex();
        try {
            ObjectReader reader = MAPPER.readerFor(Object.class)
                    .withAttribute(LocationTrackingModule.LOCATION_INDEX_KEY, index)
                    .withAttribute(LocationTrackingModule.FILE_PATH_KEY, filePath);
            Object root = reader.readValue(yaml);
            return new RawParseResult(root, index);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse YAML", e);
        }
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

        var index = new LocationIndex();
        try {
            ObjectReader reader = MAPPER.readerFor(Object.class)
                    .withAttribute(LocationTrackingModule.LOCATION_INDEX_KEY, index)
                    .withAttribute(LocationTrackingModule.FILE_PATH_KEY, filePath);
            var documents = new ArrayList<Object>();
            MappingIterator<Object> iter = reader.readValues(yaml);
            while (iter.hasNext()) {
                documents.add(iter.next());
            }

            if (documents.isEmpty() || documents.get(0) == null) {
                throw new IllegalArgumentException("YAML content must not be empty");
            }

            PlaybookDocument doc = assembleDocument(documents);
            return new ParseResult(doc, index);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse YAML", e);
        }
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
