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
import org.junit.jupiter.api.Test;

class MappingConfigTest {

    private final MappingConfig config =
            MappingConfig.load(new File("src/test/resources/schema/test-mappings.yaml"));

    @Test
    void loadsGlobalAnnotations() {
        assertThat(config.globalAnnotations()).containsExactly(
                "com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)");
    }

    @Test
    void resolvesTypeMapping() {
        assertThat(config.forType("Person")).isPresent();
        assertThat(config.forType("Unknown")).isEmpty();
    }

    @Test
    void resolvesFieldTypeAndDeserializer() {
        var person = config.forType("Person").orElseThrow();
        var address = person.forField("address").orElseThrow();
        assertThat(address.type()).isEqualTo("com.example.CustomAddress");
        assertThat(address.deserializer()).isEqualTo("com.example.AddressDeserializer");
    }

    @Test
    void resolvesAlias() {
        var person = config.forType("Person").orElseThrow();
        var name = person.forField("name").orElseThrow();
        assertThat(name.aliases()).containsExactly("fullName");
    }

    @Test
    void resolvesJsonProperty() {
        var worker = config.forType("Worker").orElseThrow();
        var doBlock = worker.forField("doBlock").orElseThrow();
        assertThat(doBlock.jsonProperty()).isEqualTo("do");
    }

    @Test
    void resolvesSkip() {
        var person = config.forType("Person").orElseThrow();
        var nickname = person.forField("nickname").orElseThrow();
        assertThat(nickname.skip()).isTrue();
    }

    @Test
    void emptyConfigHasNoOverrides() {
        MappingConfig empty = MappingConfig.empty();
        assertThat(empty.forType("Anything")).isEmpty();
        assertThat(empty.globalAnnotations()).isEmpty();
    }
}
