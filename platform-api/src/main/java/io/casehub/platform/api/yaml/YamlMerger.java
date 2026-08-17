package io.casehub.platform.api.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Generic YAML/JSON deep merge with name-keyed array support.
 *
 * <p>Merge semantics:
 * <ul>
 *   <li>Objects: recursive deep merge — overlay keys override, base keys preserved</li>
 *   <li>Named arrays: merge by key field — same key = deep merge element, new key = append</li>
 *   <li>Non-named arrays: overlay replaces base entirely</li>
 *   <li>Scalars: overlay replaces base</li>
 *   <li>Null overlay value: removes key from result (RFC 7396)</li>
 * </ul>
 */
public final class YamlMerger {

    private static final String DEFAULT_KEY_FIELD = "name";
    private static final String REMOVE_KEY = "remove";

    private YamlMerger() {}

    public static JsonNode merge(JsonNode base, JsonNode overlay) {
        return merge(base, overlay, DEFAULT_KEY_FIELD);
    }

    public static JsonNode merge(JsonNode base, JsonNode overlay, String keyField) {
        if (base == null || base.isNull()) return overlay;
        if (overlay == null || overlay.isNull()) return base;
        if (base.isObject() && overlay.isObject()) {
            return mergeObjects((ObjectNode) base, (ObjectNode) overlay, keyField);
        }
        return overlay;
    }

    private static ObjectNode mergeObjects(ObjectNode base, ObjectNode overlay, String keyField) {
        ObjectNode overlayCopy = overlay.deepCopy();
        Map<String, Set<String>> removals = extractRemovals(overlayCopy);
        ObjectNode result = base.deepCopy();
        Iterator<Map.Entry<String, JsonNode>> fields = overlayCopy.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = entry.getKey();
            JsonNode overlayValue = entry.getValue();

            if (overlayValue.isNull()) {
                result.remove(fieldName);
            } else if (result.has(fieldName)) {
                JsonNode baseValue = result.get(fieldName);
                if (baseValue.isObject() && overlayValue.isObject()) {
                    result.set(fieldName,
                        mergeObjects((ObjectNode) baseValue, (ObjectNode) overlayValue, keyField));
                } else if (baseValue.isArray() && overlayValue.isArray()) {
                    result.set(fieldName,
                        mergeArrays((ArrayNode) baseValue, (ArrayNode) overlayValue, keyField));
                } else {
                    result.set(fieldName, overlayValue.deepCopy());
                }
            } else {
                result.set(fieldName, overlayValue.deepCopy());
            }
        }
        applyRemovals(result, removals, keyField);
        return result;
    }

    private static Map<String, Set<String>> extractRemovals(ObjectNode overlay) {
        JsonNode removeNode = overlay.remove(REMOVE_KEY);
        if (removeNode == null || !removeNode.isObject()) return Map.of();
        Map<String, Set<String>> removals = new LinkedHashMap<>();
        removeNode.fields().forEachRemaining(entry -> {
            if (entry.getValue().isArray()) {
                Set<String> names = new LinkedHashSet<>();
                entry.getValue().forEach(n -> names.add(n.asText()));
                removals.put(entry.getKey(), names);
            }
        });
        return removals;
    }

    private static void applyRemovals(
            ObjectNode merged, Map<String, Set<String>> removals, String keyField) {
        for (var entry : removals.entrySet()) {
            JsonNode arrayNode = merged.get(entry.getKey());
            if (arrayNode == null || !arrayNode.isArray()) continue;
            ArrayNode filtered = merged.arrayNode();
            for (JsonNode element : arrayNode) {
                if (element.isObject() && element.has(keyField)) {
                    if (!entry.getValue().contains(element.get(keyField).asText())) {
                        filtered.add(element);
                    }
                } else {
                    filtered.add(element);
                }
            }
            merged.set(entry.getKey(), filtered);
        }
    }

    private static ArrayNode mergeArrays(ArrayNode base, ArrayNode overlay, String keyField) {
        String detectedKey = detectKeyField(base, overlay, keyField);
        if (detectedKey == null) {
            return overlay.deepCopy();
        }
        return mergeNamedArrays(base, overlay, detectedKey);
    }

    private static String detectKeyField(ArrayNode base, ArrayNode overlay, String keyField) {
        if (hasKeyField(base, keyField)) return keyField;
        if (hasKeyField(overlay, keyField)) return keyField;
        return null;
    }

    private static boolean hasKeyField(ArrayNode array, String keyField) {
        for (JsonNode element : array) {
            if (element.isObject() && element.has(keyField)) return true;
            break;
        }
        return false;
    }

    private static ArrayNode mergeNamedArrays(ArrayNode base, ArrayNode overlay, String keyField) {
        Map<String, JsonNode> merged = new LinkedHashMap<>();
        for (JsonNode element : base) {
            if (element.isObject() && element.has(keyField)) {
                merged.put(element.get(keyField).asText(), element);
            }
        }
        for (JsonNode element : overlay) {
            if (element.isObject() && element.has(keyField)) {
                String key = element.get(keyField).asText();
                if (merged.containsKey(key)) {
                    merged.put(key,
                        mergeObjects((ObjectNode) merged.get(key), (ObjectNode) element, keyField));
                } else {
                    merged.put(key, element.deepCopy());
                }
            }
        }
        ArrayNode result = base.arrayNode();
        merged.values().forEach(result::add);
        return result;
    }
}
