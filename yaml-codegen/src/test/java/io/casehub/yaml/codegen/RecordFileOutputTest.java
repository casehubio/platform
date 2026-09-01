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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordFileOutputTest {

    @TempDir Path tempDir;

    @Test
    void writesRecordFilesToDisk() throws Exception {
        TypeGraph graph =
                new SchemaParser().parse(new File("src/test/resources/schema/simple-test.yaml"));
        MappingConfig mapping = MappingConfig.empty();
        RecordEmitter.EmitConfig config =
                new RecordEmitter.EmitConfig("io.test.yaml", "Yaml");

        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(graph, mapping, config);

        Path outputDir = tempDir.resolve("io/test/yaml");
        Files.createDirectories(outputDir);
        for (RecordEmitter.GeneratedFile file : files) {
            Files.writeString(outputDir.resolve(file.fileName()), file.content());
        }

        assertThat(outputDir.resolve("YamlPerson.java")).exists();
        assertThat(outputDir.resolve("YamlAddress.java")).exists();

        String person = Files.readString(outputDir.resolve("YamlPerson.java"));
        assertThat(person).contains("package io.test.yaml;");
        assertThat(person).contains("public record YamlPerson(");
    }
}
