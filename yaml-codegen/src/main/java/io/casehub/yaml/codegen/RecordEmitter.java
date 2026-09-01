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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class RecordEmitter {

    private static final String LICENSE_HEADER =
            """
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
            """;

    private final JavaTypeResolver typeResolver = new JavaTypeResolver();

    public record EmitConfig(String targetPackage, String prefix) {}

    public record GeneratedFile(String packagePath, String fileName, String content) {}

    public List<GeneratedFile> emit(TypeGraph graph, MappingConfig mapping, EmitConfig config) {
        List<GeneratedFile> files = new ArrayList<>();
        for (TypeGraph.TypeDef typeDef : graph.types()) {
            files.add(emitRecord(typeDef, mapping, config));
        }
        return files;
    }

    private GeneratedFile emitRecord(
            TypeGraph.TypeDef typeDef, MappingConfig mapping, EmitConfig config) {
        String className = config.prefix() + typeDef.name();
        String fileName = className + ".java";
        String packagePath = config.targetPackage().replace('.', '/');

        MappingConfig.TypeMapping typeMapping = mapping.forType(typeDef.name()).orElse(null);

        List<ResolvedField> fields = resolveFields(typeDef, typeMapping, config.prefix());

        Set<String> imports = new TreeSet<>();
        for (ResolvedField f : fields) {
            imports.addAll(f.type.imports());
            imports.addAll(f.annotationImports);
        }

        for (String globalAnnotation : mapping.globalAnnotations()) {
            String annotationClass = extractAnnotationClass(globalAnnotation);
            if (annotationClass != null) {
                imports.add(annotationClass);
            }
        }

        boolean hasListFields =
                fields.stream().anyMatch(f -> f.type.typeName().startsWith("List<"));
        boolean hasMapFields =
                fields.stream().anyMatch(f -> f.type.typeName().startsWith("Map<"));
        if (hasListFields) imports.add("java.util.List");
        if (hasMapFields) imports.add("java.util.Map");

        StringBuilder sb = new StringBuilder();
        sb.append(LICENSE_HEADER);
        sb.append("package ").append(config.targetPackage()).append(";\n\n");

        for (String imp : imports) {
            if (!imp.startsWith("java.lang.")) {
                sb.append("import ").append(imp).append(";\n");
            }
        }
        if (!imports.isEmpty()) sb.append("\n");

        for (String globalAnnotation : mapping.globalAnnotations()) {
            String simpleName = extractSimpleAnnotation(globalAnnotation);
            sb.append("@").append(simpleName).append("\n");
        }

        sb.append("public record ").append(className).append("(\n");
        for (int i = 0; i < fields.size(); i++) {
            ResolvedField field = fields.get(i);
            for (String annotation : field.annotations) {
                sb.append("    ").append(annotation).append("\n");
            }
            sb.append("    ").append(field.type.typeName()).append(" ").append(field.name);
            if (i < fields.size() - 1) {
                sb.append(",\n");
            } else {
                sb.append(") {\n");
            }
        }

        List<ResolvedField> nullSafeFields =
                fields.stream()
                        .filter(
                                f ->
                                        f.type.typeName().startsWith("List<")
                                                || f.type.typeName().startsWith("Map<"))
                        .toList();

        if (!nullSafeFields.isEmpty()) {
            sb.append("\n");
            sb.append("  public ").append(className).append(" {\n");
            for (ResolvedField f : nullSafeFields) {
                String defaultValue =
                        f.type.typeName().startsWith("List<") ? "List.of()" : "Map.of()";
                sb.append("    if (").append(f.name).append(" == null) {\n");
                sb.append("      ").append(f.name).append(" = ").append(defaultValue).append(";\n");
                sb.append("    }\n");
            }
            sb.append("  }\n");
        }

        sb.append("}\n");

        return new GeneratedFile(packagePath, fileName, sb.toString());
    }

    private List<ResolvedField> resolveFields(
            TypeGraph.TypeDef typeDef, MappingConfig.TypeMapping typeMapping, String prefix) {
        List<ResolvedField> resolved = new ArrayList<>();
        for (TypeGraph.FieldDef field : typeDef.fields()) {
            MappingConfig.FieldMapping fieldMapping = null;
            String componentName = field.name();

            if (typeMapping != null) {
                fieldMapping = typeMapping.forField(field.name()).orElse(null);
                if (fieldMapping == null) {
                    fieldMapping = findByJsonProperty(typeMapping, field.name());
                    if (fieldMapping != null) {
                        componentName = findComponentNameByJsonProperty(typeMapping, field.name());
                    }
                }
            }

            if (fieldMapping != null && fieldMapping.skip()) {
                continue;
            }

            if (fieldMapping != null && fieldMapping.jsonProperty() != null) {
                componentName =
                        findComponentNameForJsonProperty(typeMapping, fieldMapping.jsonProperty());
                if (componentName == null) {
                    componentName = field.name();
                }
            }

            JavaTypeResolver.ResolvedType resolvedType =
                    typeResolver.resolve(field, fieldMapping, prefix);

            AnnotationResult ar = buildAnnotations(fieldMapping);

            resolved.add(new ResolvedField(componentName, resolvedType, ar.annotations,
                    ar.imports));
        }

        if (typeMapping != null) {
            Set<String> schemaFieldNames = typeDef.fields().stream()
                    .map(TypeGraph.FieldDef::name)
                    .collect(java.util.stream.Collectors.toSet());

            for (var entry : typeMapping.fields().entrySet()) {
                String mappingKey = entry.getKey();
                MappingConfig.FieldMapping fm = entry.getValue();
                if (fm.skip()) continue;

                boolean isSchemaField = schemaFieldNames.contains(mappingKey);
                boolean isJsonPropertyOfSchemaField = fm.jsonProperty() != null
                        && schemaFieldNames.contains(fm.jsonProperty());

                if (!isSchemaField && !isJsonPropertyOfSchemaField) {
                    String componentName = mappingKey;
                    String javaType = fm.type() != null ? fm.type()
                            : "com.fasterxml.jackson.databind.JsonNode";

                    String simpleType = simpleName(javaType);
                    Set<String> fieldImports = new TreeSet<>();
                    if (javaType.contains(".")) fieldImports.add(javaType);

                    JavaTypeResolver.ResolvedType resolvedType =
                            new JavaTypeResolver.ResolvedType(simpleType, simpleType, fieldImports);

                    AnnotationResult ar = buildAnnotations(fm);

                    resolved.add(new ResolvedField(componentName, resolvedType, ar.annotations,
                            ar.imports));
                }
            }
        }

        return resolved;
    }

    private MappingConfig.FieldMapping findByJsonProperty(
            MappingConfig.TypeMapping typeMapping, String schemaPropertyName) {
        for (var entry : typeMapping.fields().entrySet()) {
            if (schemaPropertyName.equals(entry.getValue().jsonProperty())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String findComponentNameByJsonProperty(
            MappingConfig.TypeMapping typeMapping, String schemaPropertyName) {
        for (var entry : typeMapping.fields().entrySet()) {
            if (schemaPropertyName.equals(entry.getValue().jsonProperty())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String findComponentNameForJsonProperty(
            MappingConfig.TypeMapping typeMapping, String jsonProperty) {
        for (var entry : typeMapping.fields().entrySet()) {
            if (jsonProperty.equals(entry.getValue().jsonProperty())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private record AnnotationResult(List<String> annotations, Set<String> imports) {}

    private AnnotationResult buildAnnotations(MappingConfig.FieldMapping fieldMapping) {
        List<String> annotations = new ArrayList<>();
        Set<String> imports = new TreeSet<>();
        if (fieldMapping == null) {
            return new AnnotationResult(annotations, imports);
        }
        if (fieldMapping.jsonProperty() != null) {
            annotations.add("@JsonProperty(\"" + fieldMapping.jsonProperty() + "\")");
            imports.add("com.fasterxml.jackson.annotation.JsonProperty");
        }
        if (fieldMapping.deserializer() != null) {
            String simpleDeser = simpleName(fieldMapping.deserializer());
            annotations.add("@JsonDeserialize(using = " + simpleDeser + ".class)");
            imports.add("com.fasterxml.jackson.databind.annotation.JsonDeserialize");
            imports.add(fieldMapping.deserializer());
        }
        if (!fieldMapping.aliases().isEmpty()) {
            if (fieldMapping.aliases().size() == 1) {
                annotations.add("@JsonAlias(\"" + fieldMapping.aliases().get(0) + "\")");
            } else {
                StringBuilder ab = new StringBuilder("@JsonAlias({");
                for (int i = 0; i < fieldMapping.aliases().size(); i++) {
                    if (i > 0) ab.append(", ");
                    ab.append("\"").append(fieldMapping.aliases().get(i)).append("\"");
                }
                ab.append("})");
                annotations.add(ab.toString());
            }
            imports.add("com.fasterxml.jackson.annotation.JsonAlias");
        }
        return new AnnotationResult(annotations, imports);
    }

    private String extractAnnotationClass(String globalAnnotation) {
        int parenIndex = globalAnnotation.indexOf('(');
        String className = parenIndex >= 0 ? globalAnnotation.substring(0, parenIndex) : globalAnnotation;
        return className.contains(".") ? className : null;
    }

    private String extractSimpleAnnotation(String globalAnnotation) {
        int parenIndex = globalAnnotation.indexOf('(');
        String className;
        String params;
        if (parenIndex >= 0) {
            className = globalAnnotation.substring(0, parenIndex);
            params = globalAnnotation.substring(parenIndex);
        } else {
            className = globalAnnotation;
            params = "";
        }
        return simpleName(className) + params;
    }

    private static String simpleName(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        return lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;
    }

    private record ResolvedField(
            String name,
            JavaTypeResolver.ResolvedType type,
            List<String> annotations,
            Set<String> annotationImports) {}
}
