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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PojoEmitterTest {

    @TempDir Path tempDir;

    @Test
    void generatesPojoClasses() throws Exception {
        File schema = new File("src/test/resources/schema/simple-test.yaml");
        File outputDir = tempDir.toFile();

        new PojoEmitter().emit(schema, "io.test.model", null, outputDir);

        Path packageDir = tempDir.resolve("io/test/model");
        assertThat(packageDir).exists();

        Path personFile = packageDir.resolve("Person.java");
        assertThat(personFile).exists();
        String content = Files.readString(personFile);
        assertThat(content).contains("public class Person");
        assertThat(content).contains("String name");
    }
}
