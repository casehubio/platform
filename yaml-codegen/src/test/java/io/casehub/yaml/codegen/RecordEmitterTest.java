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

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecordEmitterTest {

    private final TypeGraph graph =
            new SchemaParser().parse(new File("src/test/resources/schema/simple-test.yaml"));
    private final MappingConfig emptyMapping = MappingConfig.empty();
    private final RecordEmitter.EmitConfig config =
            new RecordEmitter.EmitConfig("io.test.yaml", "Yaml");

    @Test
    void generatesRecordPerType() {
        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(graph, emptyMapping, config);
        assertThat(files).hasSize(2);
        assertThat(files.stream().map(RecordEmitter.GeneratedFile::fileName))
                .containsExactlyInAnyOrder("YamlPerson.java", "YamlAddress.java");
    }

    @Test
    void recordHasPackageDeclaration() {
        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(graph, emptyMapping, config);
        RecordEmitter.GeneratedFile person =
                files.stream()
                        .filter(f -> f.fileName().equals("YamlPerson.java"))
                        .findFirst()
                        .orElseThrow();
        assertThat(person.content()).contains("package io.test.yaml;");
    }

    @Test
    void recordHasJsonIgnorePropertiesFromGlobalAnnotations() {
        MappingConfig withAnnotation =
                MappingConfig.load(new File("src/test/resources/schema/test-mappings.yaml"));
        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(graph, withAnnotation, config);
        RecordEmitter.GeneratedFile person =
                files.stream()
                        .filter(f -> f.fileName().equals("YamlPerson.java"))
                        .findFirst()
                        .orElseThrow();
        assertThat(person.content()).contains("@JsonIgnoreProperties(ignoreUnknown = true)");
    }

    @Test
    void recordHasNullSafeCompactConstructor() {
        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(graph, emptyMapping, config);
        RecordEmitter.GeneratedFile person =
                files.stream()
                        .filter(f -> f.fileName().equals("YamlPerson.java"))
                        .findFirst()
                        .orElseThrow();
        assertThat(person.content()).contains("if (tags == null)");
        assertThat(person.content()).contains("tags = List.of()");
    }

    @Test
    void recordHasCorrectComponents() {
        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(graph, emptyMapping, config);
        RecordEmitter.GeneratedFile person =
                files.stream()
                        .filter(f -> f.fileName().equals("YamlPerson.java"))
                        .findFirst()
                        .orElseThrow();
        assertThat(person.content()).contains("String name");
        assertThat(person.content()).contains("Integer age");
        assertThat(person.content()).contains("List<String> tags");
        assertThat(person.content()).contains("YamlAddress address");
    }

    @Test
    void mapFieldGeneratesMapType() {
        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(graph, emptyMapping, config);
        RecordEmitter.GeneratedFile address =
                files.stream()
                        .filter(f -> f.fileName().equals("YamlAddress.java"))
                        .findFirst()
                        .orElseThrow();
        assertThat(address.content()).contains("Map<String, String> metadata");
        assertThat(address.content()).contains("if (metadata == null)");
        assertThat(address.content()).contains("metadata = Map.of()");
    }

    @Test
    void mappingOverridesTypeAndAddsDeserializer() {
        MappingConfig mapping =
                MappingConfig.load(new File("src/test/resources/schema/test-mappings.yaml"));
        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(graph, mapping, config);
        RecordEmitter.GeneratedFile person =
                files.stream()
                        .filter(f -> f.fileName().equals("YamlPerson.java"))
                        .findFirst()
                        .orElseThrow();
        assertThat(person.content()).contains("CustomAddress address");
        assertThat(person.content())
                .contains("@JsonDeserialize(using = AddressDeserializer.class)");
    }

    @Test
    void mappingAddsAlias() {
        MappingConfig mapping =
                MappingConfig.load(new File("src/test/resources/schema/test-mappings.yaml"));
        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(graph, mapping, config);
        RecordEmitter.GeneratedFile person =
                files.stream()
                        .filter(f -> f.fileName().equals("YamlPerson.java"))
                        .findFirst()
                        .orElseThrow();
        assertThat(person.content()).contains("@JsonAlias(\"fullName\")");
    }

    @Test
    void skippedFieldsOmitted() {
        MappingConfig mapping =
                MappingConfig.load(new File("src/test/resources/schema/test-mappings.yaml"));
        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(graph, mapping, config);
        RecordEmitter.GeneratedFile person =
                files.stream()
                        .filter(f -> f.fileName().equals("YamlPerson.java"))
                        .findFirst()
                        .orElseThrow();
        assertThat(person.content()).doesNotContain("nickname");
    }

    @Test
    void jsonPropertyRenamesComponent() {
        MappingConfig mapping =
                MappingConfig.load(new File("src/test/resources/schema/test-mappings.yaml"));
        TypeGraph workerGraph =
                new TypeGraph(
                        List.of(
                                new TypeGraph.TypeDef(
                                        "Worker",
                                        List.of(
                                                new TypeGraph.FieldDef(
                                                        "do",
                                                        "object",
                                                        null,
                                                        false,
                                                        false,
                                                        null,
                                                        false,
                                                        null)),
                                        false,
                                        null)));
        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(workerGraph, mapping, config);
        RecordEmitter.GeneratedFile worker =
                files.stream()
                        .filter(f -> f.fileName().equals("YamlWorker.java"))
                        .findFirst()
                        .orElseThrow();
        assertThat(worker.content()).contains("@JsonProperty(\"do\")");
        assertThat(worker.content()).contains("JsonNode doBlock");
    }

    @Test
    void skipPatterns_globMatchOmitsFields() {
        TypeGraph enhancedGraph = new SchemaParser()
                                          .parse(new File("src/test/resources/schema/enhanced-test.yaml"));
        MappingConfig mapping = MappingConfig.load(
                new File("src/test/resources/schema/enhanced-mappings.yaml"));
        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(enhancedGraph, mapping, config);
        RecordEmitter.GeneratedFile event = files.stream()
                                                 .filter(f -> f.fileName().contains("Event"))
                                                 .findFirst().orElseThrow();
        assertThat(event.content()).doesNotContain("x-internal");
        assertThat(event.content()).doesNotContain("x-debug");
        assertThat(event.content()).doesNotContain("String internal");
        assertThat(event.content()).contains("String name");
    }

    @Test
    void recordName_overridesSchemaTypeName() {
        TypeGraph caseGraph = new TypeGraph(List.of(
                new TypeGraph.TypeDef("CaseDefinition", List.of(
                        new TypeGraph.FieldDef("name", "string", null, false, false, null, false, null),
                        new TypeGraph.FieldDef("duration", "object", null, false, false, null, false, null)
                                                               ), false, null)
                                                   ));
        MappingConfig mapping = MappingConfig.load(
                new File("src/test/resources/schema/enhanced-mappings.yaml"));
        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(caseGraph, mapping, new RecordEmitter.EmitConfig("io.test.yaml", ""));
        boolean hasCaseDef = files.stream()
                                  .anyMatch(f -> f.fileName().equals("CaseDef.java"));
        assertThat(hasCaseDef).as("recordName should override schema type name").isTrue();
    }

    @Test
    void defaultValue_generatesCompactConstructorGuard() {
        TypeGraph caseGraph = new TypeGraph(List.of(
                new TypeGraph.TypeDef("CaseDefinition", List.of(
                        new TypeGraph.FieldDef("name", "string", null, false, false, null, false, null),
                        new TypeGraph.FieldDef("duration", "object", null, false, false, null, false, null)
                                                               ), false, null)
                                                   ));
        MappingConfig mapping = MappingConfig.load(
                new File("src/test/resources/schema/enhanced-mappings.yaml"));
        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(caseGraph, mapping, new RecordEmitter.EmitConfig("io.test.yaml", ""));
        RecordEmitter.GeneratedFile caseDef = files.stream()
                                                   .filter(f -> f.fileName().equals("CaseDef.java"))
                                                   .findFirst().orElseThrow();
        assertThat(caseDef.content()).contains("if (name == null)");
        assertThat(caseDef.content()).contains("name = \"unnamed\"");
    }

    @Test
    void body_injectedIntoRecordBody() {
        TypeGraph caseGraph = new TypeGraph(List.of(
                new TypeGraph.TypeDef("CaseDefinition", List.of(
                        new TypeGraph.FieldDef("name", "string", null, false, false, null, false, null)
                                                               ), false, null)
                                                   ));
        MappingConfig mapping = MappingConfig.load(
                new File("src/test/resources/schema/enhanced-mappings.yaml"));
        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(caseGraph, mapping, new RecordEmitter.EmitConfig("io.test.yaml", ""));
        RecordEmitter.GeneratedFile caseDef = files.stream()
                                                   .filter(f -> f.fileName().equals("CaseDef.java"))
                                                   .findFirst().orElseThrow();
        assertThat(caseDef.content()).contains("public String key()");
    }

    @Test
    void globalImports_resolveShortTypeNames() {
        TypeGraph caseGraph = new TypeGraph(List.of(
                new TypeGraph.TypeDef("CaseDefinition", List.of(
                        new TypeGraph.FieldDef("name", "string", null, false, false, null, false, null),
                        new TypeGraph.FieldDef("duration", "object", null, false, false, null, false, null)
                                                               ), false, null)
                                                   ));
        MappingConfig mapping = MappingConfig.load(
                new File("src/test/resources/schema/enhanced-mappings.yaml"));
        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(caseGraph, mapping, new RecordEmitter.EmitConfig("io.test.yaml", ""));
        RecordEmitter.GeneratedFile caseDef = files.stream()
                                                   .filter(f -> f.fileName().equals("CaseDef.java"))
                                                   .findFirst().orElseThrow();
        assertThat(caseDef.content()).contains("import java.time.Duration;");
        assertThat(caseDef.content()).contains("Duration duration");
    }

    @Test
    void globalDeserializers_resolveImports() {
        TypeGraph caseGraph = new TypeGraph(List.of(
                new TypeGraph.TypeDef("CaseDefinition", List.of(
                        new TypeGraph.FieldDef("name", "string", null, false, false, null, false, null),
                        new TypeGraph.FieldDef("duration", "object", null, false, false, null, false, null)
                                                               ), false, null)
                                                   ));
        MappingConfig mapping = MappingConfig.load(
                new File("src/test/resources/schema/enhanced-mappings.yaml"));
        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(caseGraph, mapping, new RecordEmitter.EmitConfig("io.test.yaml", ""));
        RecordEmitter.GeneratedFile caseDef = files.stream()
                                                   .filter(f -> f.fileName().equals("CaseDef.java"))
                                                   .findFirst().orElseThrow();
        assertThat(caseDef.content()).contains("import io.casehub.deser.DurationDeserializer;");
    }

    @Test
    void existingBehavior_unchangedWithEmptyMapping() {
        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(graph, emptyMapping, config);
        assertThat(files).hasSize(2);
        assertThat(files.stream().map(RecordEmitter.GeneratedFile::fileName))
                .containsExactlyInAnyOrder("YamlPerson.java", "YamlAddress.java");
    }


}
