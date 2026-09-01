package io.casehub.schema.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class SchemaPostProcessor {

    private SchemaPostProcessor() {}

    public static JsonNode process(JsonNode schema) {
        if (schema instanceof ObjectNode root) {
            root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        }
        return schema;
    }
}
