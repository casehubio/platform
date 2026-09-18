package io.casehub.schema.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class SchemaDataGenerator {

    private static final int DEFAULT_MIN_STRING_LENGTH = 8;
    private static final int DEFAULT_MAX_STRING_LENGTH = 20;

    private final Random random;

    public SchemaDataGenerator() {
        this(new Random());
    }

    public SchemaDataGenerator(Random random) {
        this.random = random;
    }

    public List<JsonNode> generate(JsonNode schema, int count) {
        List<JsonNode> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            results.add(generateOne(schema, schema, 0));
        }
        return results;
    }

    public <T> List<T> generate(JsonNode schema, int count, Class<T> targetType, ObjectMapper mapper) {
        List<JsonNode> raw = generate(schema, count);
        List<T> results = new ArrayList<>(count);
        for (JsonNode node : raw) {
            try {
                results.add(mapper.treeToValue(node, targetType));
            } catch (Exception e) {
                throw new SchemaGenerationException("Failed to deserialize generated data to " + targetType.getName() + ": " + e.getMessage());
            }
        }
        return results;
    }

    private static final int MAX_DEPTH = 20;
    private static final int DEFAULT_MIN_ARRAY_SIZE = 1;
    private static final int DEFAULT_MAX_ARRAY_SIZE = 3;

    private JsonNode generateOne(JsonNode schema, JsonNode rootSchema, int depth) {
        if (depth > MAX_DEPTH) {
            throw new SchemaGenerationException("Maximum schema depth (" + MAX_DEPTH + ") exceeded — possible recursive $ref");
        }
        if (schema.has("$ref")) {
            return generateOne(resolveRef(schema.get("$ref").asText(), rootSchema), rootSchema, depth + 1);
        }
        if (schema.has("const")) {
            return schema.get("const").deepCopy();
        }
        if (schema.has("enum")) {
            JsonNode enumNode = schema.get("enum");
            return enumNode.get(random.nextInt(enumNode.size())).deepCopy();
        }
        String type = schema.has("type") ? schema.get("type").asText() : null;
        return switch (type) {
            case "string" -> generateString(schema);
            case "integer" -> generateInteger(schema);
            case "number" -> generateNumber(schema);
            case "boolean" -> JsonNodeFactory.instance.booleanNode(random.nextBoolean());
            case "object" -> generateObject(schema, rootSchema, depth);
            case "array" -> generateArray(schema, rootSchema, depth);
            case null, default -> JsonNodeFactory.instance.nullNode();
        };
    }

    private JsonNode resolveRef(String ref, JsonNode rootSchema) {
        if (!ref.startsWith("#/")) {
            throw new SchemaGenerationException("Unsupported $ref: " + ref);
        }
        String[] parts = ref.substring(2).split("/");
        JsonNode current = rootSchema;
        for (String part : parts) {
            current = current.get(part);
            if (current == null) {
                throw new SchemaGenerationException("Unresolvable $ref: " + ref);
            }
        }
        return current;
    }

    private JsonNode generateObject(JsonNode schema, JsonNode rootSchema, int depth) {
        com.fasterxml.jackson.databind.node.ObjectNode obj = JsonNodeFactory.instance.objectNode();
        if (!schema.has("properties")) {
            return obj;
        }
        JsonNode properties = schema.get("properties");
        boolean hasRequired = schema.has("required");
        java.util.Set<String> required = new java.util.HashSet<>();
        if (hasRequired) {
            schema.get("required").forEach(n -> required.add(n.asText()));
        }
        var fieldNames = properties.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            boolean include = !hasRequired || required.contains(fieldName) || random.nextBoolean();
            if (include) {
                obj.set(fieldName, generateOne(properties.get(fieldName), rootSchema, depth + 1));
            }
        }
        return obj;
    }

    private JsonNode generateArray(JsonNode schema, JsonNode rootSchema, int depth) {
        int minItems = schema.has("minItems") ? schema.get("minItems").asInt() : DEFAULT_MIN_ARRAY_SIZE;
        int maxItems = schema.has("maxItems") ? schema.get("maxItems").asInt() : DEFAULT_MAX_ARRAY_SIZE;
        if (maxItems < minItems) {
            maxItems = minItems;
        }
        int size = minItems + random.nextInt(maxItems - minItems + 1);
        com.fasterxml.jackson.databind.node.ArrayNode arr = JsonNodeFactory.instance.arrayNode(size);
        JsonNode itemsSchema = schema.has("items") ? schema.get("items") : JsonNodeFactory.instance.objectNode();
        for (int i = 0; i < size; i++) {
            arr.add(generateOne(itemsSchema, rootSchema, depth + 1));
        }
        return arr;
    }

    private JsonNode generateString(JsonNode schema) {
        if (schema.has("format")) {
            String format = schema.get("format").asText();
            return switch (format) {
                case "uuid" -> new TextNode(new UUID(random.nextLong(), random.nextLong()).toString());
                case "date-time" -> {
                    long offsetSeconds = random.nextInt(365 * 24 * 3600);
                    yield new TextNode(Instant.now().minus(offsetSeconds, ChronoUnit.SECONDS).toString());
                }
                default -> generatePlainString(schema);
            };
        }
        if (schema.has("pattern")) {
            return new TextNode(generateFromPattern(schema.get("pattern").asText()));
        }
        return generatePlainString(schema);
    }

    private JsonNode generatePlainString(JsonNode schema) {
        int minLen = schema.has("minLength") ? schema.get("minLength").asInt() : DEFAULT_MIN_STRING_LENGTH;
        int maxLen = schema.has("maxLength") ? schema.get("maxLength").asInt() : DEFAULT_MAX_STRING_LENGTH;
        if (maxLen < minLen) {
            maxLen = minLen;
        }
        int length = minLen + random.nextInt(maxLen - minLen + 1);
        return new TextNode(randomAlphanumeric(length));
    }

    private String generateFromPattern(String pattern) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '[') {
                int close = pattern.indexOf(']', i);
                if (close < 0) {
                    sb.append(c);
                    i++;
                    continue;
                }
                String charClass = pattern.substring(i + 1, close);
                List<Character> chars = expandCharClass(charClass);
                int repeatCount = 1;
                if (close + 1 < pattern.length() && pattern.charAt(close + 1) == '{') {
                    int braceClose = pattern.indexOf('}', close + 1);
                    if (braceClose > 0) {
                        repeatCount = Integer.parseInt(pattern.substring(close + 2, braceClose));
                        i = braceClose + 1;
                    } else {
                        i = close + 1;
                    }
                } else {
                    i = close + 1;
                }
                for (int r = 0; r < repeatCount; r++) {
                    sb.append(chars.get(random.nextInt(chars.size())));
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private List<Character> expandCharClass(String charClass) {
        List<Character> chars = new ArrayList<>();
        int i = 0;
        while (i < charClass.length()) {
            if (i + 2 < charClass.length() && charClass.charAt(i + 1) == '-') {
                char start = charClass.charAt(i);
                char end = charClass.charAt(i + 2);
                for (char ch = start; ch <= end; ch++) {
                    chars.add(ch);
                }
                i += 3;
            } else {
                chars.add(charClass.charAt(i));
                i++;
            }
        }
        return chars;
    }

    private JsonNode generateInteger(JsonNode schema) {
        int min = schema.has("minimum") ? schema.get("minimum").asInt() : Integer.MIN_VALUE / 2;
        int max = schema.has("maximum") ? schema.get("maximum").asInt() : Integer.MAX_VALUE / 2;
        long range = (long) max - min + 1;
        int value = (int) (min + (Math.abs(random.nextLong()) % range));
        return JsonNodeFactory.instance.numberNode(value);
    }

    private JsonNode generateNumber(JsonNode schema) {
        double min = schema.has("minimum") ? schema.get("minimum").asDouble() : -1000.0;
        double max = schema.has("maximum") ? schema.get("maximum").asDouble() : 1000.0;
        double value = min + random.nextDouble() * (max - min);
        return JsonNodeFactory.instance.numberNode(value);
    }

    private String randomAlphanumeric(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
