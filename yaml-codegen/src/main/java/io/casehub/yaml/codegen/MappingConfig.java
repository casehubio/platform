/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.yaml.codegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record MappingConfig(List<String> globalAnnotations, Map<String, TypeMapping> types) {

    public Optional<TypeMapping> forType(String typeName) {
        return Optional.ofNullable(types.get(typeName));
    }

    public static MappingConfig empty() {
        return new MappingConfig(List.of(), Map.of());
    }

    public static MappingConfig load(File file) {
        try {
            ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
            JsonNode root = yaml.readTree(file);

            List<String> annotations = new ArrayList<>();
            JsonNode globalAnns = root.path("globalAnnotations");
            if (globalAnns.isArray()) {
                globalAnns.forEach(n -> annotations.add(n.asText()));
            }

            Map<String, TypeMapping> typeMap = new HashMap<>();
            JsonNode typesNode = root.path("types");
            Iterator<Map.Entry<String, JsonNode>> it = typesNode.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                typeMap.put(entry.getKey(), parseTypeMapping(entry.getValue()));
            }

            return new MappingConfig(List.copyOf(annotations), Map.copyOf(typeMap));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static TypeMapping parseTypeMapping(JsonNode node) {
        Map<String, FieldMapping> fields = new HashMap<>();
        JsonNode fieldsNode = node.path("fields");
        Iterator<Map.Entry<String, JsonNode>> it = fieldsNode.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            fields.put(entry.getKey(), parseFieldMapping(entry.getValue()));
        }

        List<FieldMapping> additional = new ArrayList<>();
        JsonNode addFieldsNode = node.path("additionalFields");
        if (addFieldsNode.isArray()) {
            for (JsonNode fieldNode : addFieldsNode) {
                String name = fieldNode.get("name").asText();
                FieldMapping fm = parseFieldMapping(fieldNode);
                fields.put(name, fm);
                additional.add(fm);
            }
        }

        return new TypeMapping(Map.copyOf(fields), List.copyOf(additional));
    }

    private static FieldMapping parseFieldMapping(JsonNode node) {
        String name = node.has("name") ? node.get("name").asText() : null;
        String type = node.has("type") ? node.get("type").asText() : null;
        String deserializer = node.has("deserializer") ? node.get("deserializer").asText() : null;
        String jsonProperty = node.has("jsonProperty") ? node.get("jsonProperty").asText() : null;
        boolean skip = node.has("skip") && node.get("skip").asBoolean();

        List<String> aliases = new ArrayList<>();
        JsonNode aliasNode = node.path("alias");
        if (aliasNode.isArray()) {
            aliasNode.forEach(n -> aliases.add(n.asText()));
        } else if (aliasNode.isTextual()) {
            aliases.add(aliasNode.asText());
        }

        return new FieldMapping(name, type, deserializer, List.copyOf(aliases), jsonProperty, skip);
    }

    public record TypeMapping(Map<String, FieldMapping> fields, List<FieldMapping> additionalFields) {
        public TypeMapping(Map<String, FieldMapping> fields) {
            this(fields, List.of());
        }

        public Optional<FieldMapping> forField(String fieldName) {
            return Optional.ofNullable(fields.get(fieldName));
        }
    }

    public record FieldMapping(
            String name,
            String type,
            String deserializer,
            List<String> aliases,
            String jsonProperty,
            boolean skip) {}
}
