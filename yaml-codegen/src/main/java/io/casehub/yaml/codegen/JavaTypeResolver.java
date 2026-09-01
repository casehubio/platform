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

import java.util.Set;
import java.util.TreeSet;

public class JavaTypeResolver {

    public record ResolvedType(String typeName, String simpleTypeName, Set<String> imports) {}

    public ResolvedType resolve(
            TypeGraph.FieldDef field,
            MappingConfig.FieldMapping mapping,
            String prefix) {

        Set<String> imports = new TreeSet<>();

        if (mapping != null && mapping.type() != null) {
            String fqcn = mapping.type();
            String simple = simpleName(fqcn);
            if (fqcn.contains(".")) {
                imports.add(fqcn);
            }
            return new ResolvedType(simple, simple, imports);
        }

        if (field.isArray()) {
            imports.add("java.util.List");
            String itemType = resolveItemType(field, prefix, imports);
            return new ResolvedType("List<" + itemType + ">", "List", imports);
        }

        if (field.isMap()) {
            imports.add("java.util.Map");
            String valueType = resolveMapValueType(field, prefix, imports);
            return new ResolvedType("Map<String, " + valueType + ">", "Map", imports);
        }

        if (field.refTarget() != null) {
            String refType = prefix + field.refTarget();
            return new ResolvedType(refType, refType, imports);
        }

        return resolveSchemaType(field.schemaType(), imports);
    }

    private String resolveItemType(
            TypeGraph.FieldDef field, String prefix, Set<String> imports) {
        if (field.refTarget() != null) {
            return prefix + field.refTarget();
        }
        return mapPrimitiveType(field.schemaType(), imports);
    }

    private String resolveMapValueType(
            TypeGraph.FieldDef field, String prefix, Set<String> imports) {
        String valueType = field.mapValueType();
        if (valueType == null || "object".equals(valueType)) {
            return "Object";
        }
        if (Character.isUpperCase(valueType.charAt(0))) {
            return prefix + valueType;
        }
        return mapPrimitiveType(valueType, imports);
    }

    private ResolvedType resolveSchemaType(String schemaType, Set<String> imports) {
        if (schemaType == null || "oneOf".equals(schemaType)) {
            imports.add("com.fasterxml.jackson.databind.JsonNode");
            return new ResolvedType("JsonNode", "JsonNode", imports);
        }
        String javaType = mapPrimitiveType(schemaType, imports);
        return new ResolvedType(javaType, javaType, imports);
    }

    private String mapPrimitiveType(String schemaType, Set<String> imports) {
        if (schemaType == null) {
            imports.add("com.fasterxml.jackson.databind.JsonNode");
            return "JsonNode";
        }
        return switch (schemaType) {
            case "string" -> "String";
            case "integer" -> "Integer";
            case "number" -> "Double";
            case "boolean" -> "Boolean";
            case "object" -> {
                imports.add("com.fasterxml.jackson.databind.JsonNode");
                yield "JsonNode";
            }
            default -> {
                imports.add("com.fasterxml.jackson.databind.JsonNode");
                yield "JsonNode";
            }
        };
    }

    private static String simpleName(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        return lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;
    }
}
