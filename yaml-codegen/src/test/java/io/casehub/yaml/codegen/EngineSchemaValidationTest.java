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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EngineSchemaValidationTest {

    private static List<RecordEmitter.GeneratedFile> generatedFiles;

    @BeforeAll
    static void generateFromEngineSchema() {
        TypeGraph graph =
                new SchemaParser()
                        .parse(new File("src/test/resources/schema/CaseDefinition.yaml"));
        MappingConfig mapping =
                MappingConfig.load(
                        new File("src/test/resources/schema/engine-record-mappings.yaml"));
        RecordEmitter.EmitConfig config =
                new RecordEmitter.EmitConfig("io.casehub.api.model.converter.yaml", "Yaml");
        generatedFiles = new RecordEmitter().emit(graph, mapping, config);
    }

    @Test
    void generatesMultipleTypes() {
        assertThat(generatedFiles.size()).isGreaterThanOrEqualTo(10);
    }

    @Test
    void generatesBindingRecord() {
        RecordEmitter.GeneratedFile binding = findFile("YamlBinding.java");
        assertThat(binding.content())
                .contains("@JsonDeserialize(using = TriggerDeserializer.class)");
        assertThat(binding.content()).contains("Trigger on");
        assertThat(binding.content()).contains("@JsonAlias(\"replanAfter\")");
        assertThat(binding.content()).contains("List<String> producedKeys");
        assertThat(binding.content()).contains("List<String> contingency");
    }

    @Test
    void generatesWorkerRecord() {
        RecordEmitter.GeneratedFile worker = findFile("YamlWorker.java");
        assertThat(worker.content()).contains("@JsonProperty(\"do\")");
        assertThat(worker.content()).contains("JsonNode doBlock");
        assertThat(worker.content()).contains("List<String> capabilities");
    }

    @Test
    void generatesCaseDefinitionSpecRecord() {
        RecordEmitter.GeneratedFile spec = findFile("YamlCaseDefinitionSpec.java");
        assertThat(spec.content())
                .contains("@JsonDeserialize(using = CaseCompletionDeserializer.class)");
        assertThat(spec.content()).contains("@JsonAlias(\"cbr\")");
        assertThat(spec.content()).contains("@JsonAlias(\"adaptation\")");
        assertThat(spec.content()).contains("@JsonAlias(\"reflection\")");
        assertThat(spec.content()).contains("@JsonAlias(\"goapActions\")");
    }

    @Test
    void generatesCapabilityRecord() {
        RecordEmitter.GeneratedFile cap = findFile("YamlCapability.java");
        assertThat(cap.content()).contains("@JsonAlias(\"inputSchema\")");
        assertThat(cap.content()).contains("@JsonAlias(\"outputSchema\")");
    }

    @Test
    void generatesGoalRecord() {
        RecordEmitter.GeneratedFile goal = findFile("YamlGoal.java");
        assertThat(goal.content()).contains("@JsonAlias(\"condition\")");
        assertThat(goal.content()).contains("ExpressionEvaluator when");
    }

    @Test
    void generatesMilestoneRecord() {
        RecordEmitter.GeneratedFile milestone = findFile("YamlMilestone.java");
        assertThat(milestone.content()).contains("@JsonAlias({\"condition\", \"completionCriteria\"})");
        assertThat(milestone.content()).contains("ExpressionEvaluator when");
    }

    @Test
    void allRecordsHaveJsonIgnoreProperties() {
        for (RecordEmitter.GeneratedFile file : generatedFiles) {
            assertThat(file.content())
                    .as("File %s should have @JsonIgnoreProperties", file.fileName())
                    .contains("@JsonIgnoreProperties(ignoreUnknown = true)");
        }
    }

    @Test
    void allRecordsHaveNullSafeDefaults() {
        RecordEmitter.GeneratedFile binding = findFile("YamlBinding.java");
        assertThat(binding.content()).contains("if (producedKeys == null)");
        assertThat(binding.content()).contains("producedKeys = List.of()");
        assertThat(binding.content()).contains("if (contingency == null)");
        assertThat(binding.content()).contains("contingency = List.of()");
    }

    @Test
    void generatesSubCaseRecord() {
        RecordEmitter.GeneratedFile subCase = findFile("YamlSubCase.java");
        assertThat(subCase.content())
                .contains("@JsonDeserialize(using = SubCaseMappingDeserializer.class)");
        assertThat(subCase.content()).contains("SubCaseMapping inputMapping");
        assertThat(subCase.content()).contains("SubCaseMapping outputMapping");
    }

    private RecordEmitter.GeneratedFile findFile(String fileName) {
        return generatedFiles.stream()
                .filter(f -> f.fileName().equals(fileName))
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "Generated file not found: "
                                                + fileName
                                                + ". Available: "
                                                + generatedFiles.stream()
                                                        .map(RecordEmitter.GeneratedFile::fileName)
                                                        .collect(Collectors.joining(", "))));
    }
}
