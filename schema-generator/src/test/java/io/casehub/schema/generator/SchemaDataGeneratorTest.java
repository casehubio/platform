package io.casehub.schema.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaDataGeneratorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void generates_random_string_from_string_schema() {
        ObjectNode schema = mapper.createObjectNode().put("type", "string");

        var generator = new SchemaDataGenerator(new Random(42));
        List<JsonNode> results = generator.generate(schema, 3);

        assertThat(results).hasSize(3);
        for (JsonNode node : results) {
            assertThat(node.isTextual()).isTrue();
            assertThat(node.asText()).isNotEmpty();
        }
    }

    @Test
    void generates_random_integer_from_integer_schema() {
        ObjectNode schema = mapper.createObjectNode().put("type", "integer");

        var generator = new SchemaDataGenerator(new Random(42));
        List<JsonNode> results = generator.generate(schema, 3);

        assertThat(results).hasSize(3);
        for (JsonNode node : results) {
            assertThat(node.isIntegralNumber()).isTrue();
        }
    }

    @Test
    void generates_random_number_from_number_schema() {
        ObjectNode schema = mapper.createObjectNode().put("type", "number");

        var generator = new SchemaDataGenerator(new Random(42));
        List<JsonNode> results = generator.generate(schema, 3);

        assertThat(results).hasSize(3);
        for (JsonNode node : results) {
            assertThat(node.isNumber()).isTrue();
        }
    }

    @Test
    void generates_random_boolean_from_boolean_schema() {
        ObjectNode schema = mapper.createObjectNode().put("type", "boolean");

        var generator = new SchemaDataGenerator(new Random(42));
        List<JsonNode> results = generator.generate(schema, 5);

        assertThat(results).hasSize(5);
        for (JsonNode node : results) {
            assertThat(node.isBoolean()).isTrue();
        }
    }

    @Test
    void selects_random_value_from_enum() {
        ObjectNode schema = mapper.createObjectNode().put("type", "string");
        ArrayNode enumValues = schema.putArray("enum");
        enumValues.add("RED");
        enumValues.add("GREEN");
        enumValues.add("BLUE");

        var generator = new SchemaDataGenerator(new Random(42));
        List<JsonNode> results = generator.generate(schema, 10);

        assertThat(results).hasSize(10);
        List<String> allowed = List.of("RED", "GREEN", "BLUE");
        for (JsonNode node : results) {
            assertThat(node.asText()).isIn(allowed);
        }
    }

    @Test
    void returns_const_value() {
        ObjectNode schema = mapper.createObjectNode().put("type", "string");
        schema.put("const", "fixed-value");

        var generator = new SchemaDataGenerator(new Random(42));
        List<JsonNode> results = generator.generate(schema, 3);

        assertThat(results).hasSize(3);
        for (JsonNode node : results) {
            assertThat(node.asText()).isEqualTo("fixed-value");
        }
    }

    @Test
    void respects_string_min_and_max_length() {
        ObjectNode schema = mapper.createObjectNode()
                .put("type", "string")
                .put("minLength", 5)
                .put("maxLength", 10);

        var generator = new SchemaDataGenerator(new Random(42));
        List<JsonNode> results = generator.generate(schema, 20);

        for (JsonNode node : results) {
            int len = node.asText().length();
            assertThat(len).isBetween(5, 10);
        }
    }

    @Test
    void respects_integer_minimum_and_maximum() {
        ObjectNode schema = mapper.createObjectNode()
                .put("type", "integer")
                .put("minimum", 10)
                .put("maximum", 20);

        var generator = new SchemaDataGenerator(new Random(42));
        List<JsonNode> results = generator.generate(schema, 20);

        for (JsonNode node : results) {
            assertThat(node.asInt()).isBetween(10, 20);
        }
    }

    @Test
    void respects_number_minimum_and_maximum() {
        ObjectNode schema = mapper.createObjectNode()
                .put("type", "number")
                .put("minimum", -5.0)
                .put("maximum", 5.0);

        var generator = new SchemaDataGenerator(new Random(42));
        List<JsonNode> results = generator.generate(schema, 20);

        for (JsonNode node : results) {
            assertThat(node.asDouble()).isBetween(-5.0, 5.0);
        }
    }

    @Test
    void generates_valid_uuid_for_format_uuid() {
        ObjectNode schema = mapper.createObjectNode()
                .put("type", "string")
                .put("format", "uuid");

        var generator = new SchemaDataGenerator(new Random(42));
        List<JsonNode> results = generator.generate(schema, 3);

        for (JsonNode node : results) {
            assertThat(node.isTextual()).isTrue();
            UUID.fromString(node.asText());
        }
    }

    @Test
    void generates_valid_iso8601_for_format_date_time() {
        ObjectNode schema = mapper.createObjectNode()
                .put("type", "string")
                .put("format", "date-time");

        var generator = new SchemaDataGenerator(new Random(42));
        List<JsonNode> results = generator.generate(schema, 3);

        for (JsonNode node : results) {
            assertThat(node.isTextual()).isTrue();
            Instant.parse(node.asText());
        }
    }

    @Test
    void seeded_random_produces_identical_output() {
        ObjectNode schema = mapper.createObjectNode().put("type", "string");

        List<JsonNode> first = new SchemaDataGenerator(new Random(99)).generate(schema, 5);
        List<JsonNode> second = new SchemaDataGenerator(new Random(99)).generate(schema, 5);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void generates_string_matching_simple_pattern() {
        ObjectNode schema = mapper.createObjectNode()
                .put("type", "string")
                .put("pattern", "[A-Z]{3}-[0-9]{4}");

        var generator = new SchemaDataGenerator(new Random(42));
        List<JsonNode> results = generator.generate(schema, 5);

        for (JsonNode node : results) {
            assertThat(node.isTextual()).isTrue();
            assertThat(node.asText()).matches("[A-Z]{3}-[0-9]{4}");
        }
    }

    @Test
    void generates_object_with_required_properties() {
        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("name").put("type", "string");
        props.putObject("age").put("type", "integer");
        props.putObject("email").put("type", "string");
        ArrayNode required = schema.putArray("required");
        required.add("name");
        required.add("age");

        var generator = new SchemaDataGenerator(new Random(42));
        List<JsonNode> results = generator.generate(schema, 3);

        assertThat(results).hasSize(3);
        for (JsonNode node : results) {
            assertThat(node.isObject()).isTrue();
            assertThat(node.has("name")).isTrue();
            assertThat(node.get("name").isTextual()).isTrue();
            assertThat(node.has("age")).isTrue();
            assertThat(node.get("age").isIntegralNumber()).isTrue();
        }
    }

    @Test
    void generates_array_with_min_and_max_items() {
        ObjectNode schema = mapper.createObjectNode()
                .put("type", "array")
                .put("minItems", 2)
                .put("maxItems", 4);
        schema.putObject("items").put("type", "integer");

        var generator = new SchemaDataGenerator(new Random(42));
        List<JsonNode> results = generator.generate(schema, 10);

        assertThat(results).hasSize(10);
        for (JsonNode node : results) {
            assertThat(node.isArray()).isTrue();
            assertThat(node.size()).isBetween(2, 4);
            for (JsonNode element : node) {
                assertThat(element.isIntegralNumber()).isTrue();
            }
        }
    }

    @Test
    void resolves_ref_to_defs() {
        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("address").put("$ref", "#/$defs/Address");
        schema.putArray("required").add("address");

        ObjectNode defs = schema.putObject("$defs");
        ObjectNode addressDef = defs.putObject("Address").put("type", "object");
        ObjectNode addrProps = addressDef.putObject("properties");
        addrProps.putObject("street").put("type", "string");
        addrProps.putObject("zip").put("type", "integer");
        addressDef.putArray("required").add("street").add("zip");

        var generator = new SchemaDataGenerator(new Random(42));
        List<JsonNode> results = generator.generate(schema, 2);

        assertThat(results).hasSize(2);
        for (JsonNode node : results) {
            assertThat(node.has("address")).isTrue();
            JsonNode addr = node.get("address");
            assertThat(addr.isObject()).isTrue();
            assertThat(addr.has("street")).isTrue();
            assertThat(addr.get("street").isTextual()).isTrue();
            assertThat(addr.has("zip")).isTrue();
            assertThat(addr.get("zip").isIntegralNumber()).isTrue();
        }
    }

    @Test
    void throws_on_recursive_ref_exceeding_depth() {
        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("child").put("$ref", "#/$defs/Node");
        schema.putArray("required").add("child");

        ObjectNode defs = schema.putObject("$defs");
        ObjectNode nodeDef = defs.putObject("Node").put("type", "object");
        ObjectNode nodeProps = nodeDef.putObject("properties");
        nodeProps.putObject("child").put("$ref", "#/$defs/Node");
        nodeDef.putArray("required").add("child");

        var generator = new SchemaDataGenerator(new Random(42));
        assertThatThrownBy(() -> generator.generate(schema, 1))
                .isInstanceOf(SchemaGenerationException.class);
    }

    @Test
    void typed_api_deserializes_to_target_type() {
        record Point(int x, int y) {}

        var schemaGen = new PlatformSchemaGenerator();
        JsonNode schema = schemaGen.generate(Point.class);

        var dataGen = new SchemaDataGenerator(new Random(42));
        List<Point> points = dataGen.generate(schema, 3, Point.class, mapper);

        assertThat(points).hasSize(3);
        for (Point p : points) {
            assertThat(p).isNotNull();
        }
    }
}
