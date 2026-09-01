package io.casehub.schema.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.Module;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformSchemaGeneratorTest {

    record SimpleRecord(String name, int count, boolean active) {}

    enum Color { RED, GREEN, BLUE }

    record WithEnum(String label, Color color) {}

    @Test
    void generates_schema_for_simple_record() {
        var generator = new PlatformSchemaGenerator();
        JsonNode schema = generator.generate(SimpleRecord.class);

        assertThat(schema.has("properties")).isTrue();
        assertThat(schema.get("properties").has("name")).isTrue();
        assertThat(schema.get("properties").has("count")).isTrue();
        assertThat(schema.get("properties").has("active")).isTrue();
    }

    @Test
    void includes_schema_version_draft_2020_12() {
        var generator = new PlatformSchemaGenerator();
        JsonNode schema = generator.generate(SimpleRecord.class);

        assertThat(schema.get("$schema").asText())
                .contains("2020-12");
    }

    @Test
    void enum_values_inlined() {
        var generator = new PlatformSchemaGenerator();
        JsonNode schema = generator.generate(WithEnum.class);

        JsonNode colorProp = schema.get("properties").get("color");
        assertThat(colorProp.has("enum")).isTrue();
        List<String> values = new ArrayList<>();
        colorProp.get("enum").forEach(v -> values.add(v.asText()));
        assertThat(values).containsExactlyInAnyOrder("RED", "GREEN", "BLUE");
    }

    @Test
    void custom_module_applied() {
        Module custom = configBuilder ->
                configBuilder.forFields()
                        .withDescriptionResolver(field -> "custom-desc");
        var generator = new PlatformSchemaGenerator(custom);
        JsonNode schema = generator.generate(SimpleRecord.class);

        JsonNode nameProp = schema.get("properties").get("name");
        assertThat(nameProp.get("description").asText())
                .isEqualTo("custom-desc");
    }

    @Test
    void generate_to_json_writes_file(@TempDir Path tempDir) throws Exception {
        var generator = new PlatformSchemaGenerator();
        Path output = tempDir.resolve("schema.json");
        generator.generateToJson(SimpleRecord.class, output);

        assertThat(Files.exists(output)).isTrue();
        String content = Files.readString(output);
        assertThat(content).contains("\"properties\"");
        assertThat(content).contains("\"$schema\"");
    }
}
