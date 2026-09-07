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
        MappingConfig.TypeMapping typeMapping = mapping.forType(typeDef.name()).orElse(null);

        String className;
        if (typeMapping != null && typeMapping.recordName() != null) {
            className = typeMapping.recordName();
        } else {
            className = config.prefix() + typeDef.name();
        }
        String fileName    = className + ".java";
        String packagePath = config.targetPackage().replace('.', '/');

        List<ResolvedField> fields = resolveFields(typeDef, typeMapping, mapping, config.prefix());

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
        if (hasListFields) {imports.add("java.util.List");}
        if (hasMapFields) {imports.add("java.util.Map");}

        StringBuilder sb = new StringBuilder();
        sb.append(LICENSE_HEADER);
        sb.append("package ").append(config.targetPackage()).append(";\n\n");

        for (String imp : imports) {
            if (!imp.startsWith("java.lang.")) {
                sb.append("import ").append(imp).append(";\n");
            }
        }
        if (!imports.isEmpty()) {sb.append("\n");}

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

        List<ResolvedField> defaultableFields = fields.stream()
                                                      .filter(f -> f.defaultValue != null
                                                                   || f.type.typeName().startsWith("List<")
                                                                   || f.type.typeName().startsWith("Map<"))
                                                      .toList();

        boolean hasBody = typeMapping != null && typeMapping.body() != null
                          && !typeMapping.body().isBlank();

        if (!defaultableFields.isEmpty() || hasBody) {
            if (!defaultableFields.isEmpty()) {
                sb.append("\n");
                sb.append("  public ").append(className).append(" {\n");
                for (ResolvedField f : defaultableFields) {
                    String defVal;
                    if (f.defaultValue != null) {
                        defVal = f.defaultValue;
                    } else if (f.type.typeName().startsWith("List<")) {
                        defVal = "List.of()";
                    } else {
                        defVal = "Map.of()";
                    }
                    sb.append("    if (").append(f.name).append(" == null) {\n");
                    sb.append("      ").append(f.name).append(" = ").append(defVal).append(";\n");
                    sb.append("    }\n");
                }
                sb.append("  }\n");
            }
            if (hasBody) {
                sb.append("\n");
                for (String line : typeMapping.body().lines().toList()) {
                    sb.append("  ").append(line).append("\n");
                }
            }
        }

        sb.append("}\n");

        return new GeneratedFile(packagePath, fileName, sb.toString());
    }

    private List<ResolvedField> resolveFields(
            TypeGraph.TypeDef typeDef, MappingConfig.TypeMapping typeMapping,
            MappingConfig mapping, String prefix) {
        List<ResolvedField> resolved = new ArrayList<>();
        for (TypeGraph.FieldDef field : typeDef.fields()) {
            if (shouldSkipGlobal(field.name(), mapping)) {
                continue;
            }

            MappingConfig.FieldMapping fieldMapping  = null;
            String                     componentName = field.name();

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
                    typeResolver.resolve(field, fieldMapping, prefix, mapping);

            AnnotationResult ar           = buildAnnotations(fieldMapping, mapping);
            String           defaultValue = fieldMapping != null ? fieldMapping.defaultValue() : null;

            resolved.add(new ResolvedField(componentName, resolvedType, ar.annotations,
                                           ar.imports, defaultValue));
        }

        if (typeMapping != null) {
            Set<String> schemaFieldNames = typeDef.fields().stream()
                                                  .map(TypeGraph.FieldDef::name)
                                                  .collect(java.util.stream.Collectors.toSet());

            for (var entry : typeMapping.fields().entrySet()) {
                String                     mappingKey = entry.getKey();
                MappingConfig.FieldMapping fm         = entry.getValue();
                if (fm.skip()) {continue;}

                boolean isSchemaField = schemaFieldNames.contains(mappingKey);
                boolean isJsonPropertyOfSchemaField = fm.jsonProperty() != null
                                                      && schemaFieldNames.contains(fm.jsonProperty());

                if (!isSchemaField && !isJsonPropertyOfSchemaField) {
                    String componentName = mappingKey;
                    String javaType = fm.type() != null ? fm.type()
                                                        : "com.fasterxml.jackson.databind.JsonNode";

                    String      resolvedFqcn = resolveTypeFqcn(javaType, mapping);
                    String      simpleType   = simpleName(resolvedFqcn);
                    Set<String> fieldImports = new TreeSet<>();
                    if (resolvedFqcn.contains(".")) {fieldImports.add(resolvedFqcn);}

                    JavaTypeResolver.ResolvedType resolvedType =
                            new JavaTypeResolver.ResolvedType(simpleType, simpleType, fieldImports);

                    AnnotationResult ar = buildAnnotations(fm, mapping);

                    resolved.add(new ResolvedField(componentName, resolvedType, ar.annotations,
                                                   ar.imports, fm.defaultValue()));
                }
            }
        }

        return resolved;
    }

    private boolean shouldSkipGlobal(String fieldName, MappingConfig mapping) {
        for (String pattern : mapping.skipPatterns()) {
            if (pattern.endsWith("*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (fieldName.startsWith(prefix)) {return true;}
            } else if (pattern.equals(fieldName)) {
                return true;
            }
        }
        return false;
    }

    private String resolveTypeFqcn(String typeName, MappingConfig mapping) {
        if (typeName.contains(".")) {return typeName;}
        String fqcn = mapping.imports().get(typeName);
        return fqcn != null ? fqcn : typeName;
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

    private AnnotationResult buildAnnotations(MappingConfig.FieldMapping fieldMapping,
                                              MappingConfig mapping) {
        List<String> annotations = new ArrayList<>();
        Set<String>  imports     = new TreeSet<>();
        if (fieldMapping == null) {
            return new AnnotationResult(annotations, imports);
        }
        if (fieldMapping.jsonProperty() != null) {
            annotations.add("@JsonProperty(\"" + fieldMapping.jsonProperty() + "\")");
            imports.add("com.fasterxml.jackson.annotation.JsonProperty");
        }
        if (fieldMapping.deserializer() != null) {
            String deserFqcn   = resolveDeserializerFqcn(fieldMapping.deserializer(), mapping);
            String simpleDeser = simpleName(deserFqcn);
            annotations.add("@JsonDeserialize(using = " + simpleDeser + ".class)");
            imports.add("com.fasterxml.jackson.databind.annotation.JsonDeserialize");
            if (deserFqcn.contains(".")) {
                imports.add(deserFqcn);
            }
        }
        if (!fieldMapping.aliases().isEmpty()) {
            if (fieldMapping.aliases().size() == 1) {
                annotations.add("@JsonAlias(\"" + fieldMapping.aliases().get(0) + "\")");
            } else {
                StringBuilder ab = new StringBuilder("@JsonAlias({");
                for (int i = 0; i < fieldMapping.aliases().size(); i++) {
                    if (i > 0) {ab.append(", ");}
                    ab.append("\"").append(fieldMapping.aliases().get(i)).append("\"");
                }
                ab.append("})");
                annotations.add(ab.toString());
            }
            imports.add("com.fasterxml.jackson.annotation.JsonAlias");
        }
        return new AnnotationResult(annotations, imports);
    }

    private String resolveDeserializerFqcn(String deserializer, MappingConfig mapping) {
        if (deserializer.contains(".")) {return deserializer;}
        String fqcn = mapping.deserializers().get(deserializer);
        return fqcn != null ? fqcn : deserializer;
    }

    private String extractAnnotationClass(String globalAnnotation) {
        int    parenIndex = globalAnnotation.indexOf('(');
        String className  = parenIndex >= 0 ? globalAnnotation.substring(0, parenIndex) : globalAnnotation;
        return className.contains(".") ? className : null;
    }

    private String extractSimpleAnnotation(String globalAnnotation) {
        int    parenIndex = globalAnnotation.indexOf('(');
        String className;
        String params;
        if (parenIndex >= 0) {
            className = globalAnnotation.substring(0, parenIndex);
            params    = globalAnnotation.substring(parenIndex);
        } else {
            className = globalAnnotation;
            params    = "";
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
            Set<String> annotationImports,
            String defaultValue) {}
}
