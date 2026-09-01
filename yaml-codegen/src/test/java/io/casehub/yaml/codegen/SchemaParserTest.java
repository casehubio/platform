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

class SchemaParserTest {

    private final TypeGraph graph =
            new SchemaParser().parse(new File("src/test/resources/schema/simple-test.yaml"));

    @Test
    void parsesDefsIntoTypeGraph() {
        assertThat(graph.types()).hasSize(2);
        assertThat(graph.types().stream().map(TypeGraph.TypeDef::name))
                .containsExactlyInAnyOrder("Person", "Address");
    }

    @Test
    void extractsFieldsFromProperties() {
        TypeGraph.TypeDef person = graph.findType("Person").orElseThrow();
        assertThat(person.fields()).hasSize(4);
        assertThat(person.fields().stream().map(TypeGraph.FieldDef::name))
                .containsExactly("name", "age", "tags", "address");
    }

    @Test
    void detectsArrayFields() {
        TypeGraph.TypeDef person = graph.findType("Person").orElseThrow();
        TypeGraph.FieldDef tags = person.findField("tags").orElseThrow();
        assertThat(tags.isArray()).isTrue();
        assertThat(tags.schemaType()).isEqualTo("string");
    }

    @Test
    void detectsRefFields() {
        TypeGraph.TypeDef person = graph.findType("Person").orElseThrow();
        TypeGraph.FieldDef address = person.findField("address").orElseThrow();
        assertThat(address.refTarget()).isEqualTo("Address");
    }

    @Test
    void detectsMapFields() {
        TypeGraph.TypeDef addr = graph.findType("Address").orElseThrow();
        TypeGraph.FieldDef metadata = addr.findField("metadata").orElseThrow();
        assertThat(metadata.isMap()).isTrue();
        assertThat(metadata.mapValueType()).isEqualTo("string");
    }

    @Test
    void detectsRequiredFields() {
        TypeGraph.TypeDef person = graph.findType("Person").orElseThrow();
        assertThat(person.findField("name").orElseThrow().required()).isTrue();
        assertThat(person.findField("age").orElseThrow().required()).isFalse();
    }
}
