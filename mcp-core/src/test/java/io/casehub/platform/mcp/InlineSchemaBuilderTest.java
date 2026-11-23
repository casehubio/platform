package io.casehub.platform.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InlineSchemaBuilderTest {

    record Tag(String label, String value) {}

    record Wrapper(List<Tag> primary, List<Tag> secondary) {}

    enum Colour { RED, GREEN, BLUE }

    @Test
    void stringMapsToStringType() {
        var schema = InlineSchemaBuilder.typeToSchema(String.class);
        assertThat(schema.get("type")).isEqualTo("string");
    }

    @Test
    void intMapsToIntegerType() {
        var schema = InlineSchemaBuilder.typeToSchema(int.class);
        assertThat(schema.get("type")).isEqualTo("integer");
    }

    @Test
    void booleanMapsToBooleanType() {
        var schema = InlineSchemaBuilder.typeToSchema(boolean.class);
        assertThat(schema.get("type")).isEqualTo("boolean");
    }

    @Test
    void doubleMapsToNumberType() {
        var schema = InlineSchemaBuilder.typeToSchema(double.class);
        assertThat(schema.get("type")).isEqualTo("number");
    }

    @Test
    void enumMapsToStringWithEnumValues() {
        var schema = InlineSchemaBuilder.typeToSchema(Colour.class);
        assertThat(schema.get("type")).isEqualTo("string");
        assertThat(schema.get("enum")).isEqualTo(List.of("RED", "GREEN", "BLUE"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordInlinesProperties() {
        var schema = InlineSchemaBuilder.typeToSchema(Tag.class);
        assertThat(schema.get("type")).isEqualTo("object");
        var props = (Map<String, Object>) schema.get("properties");
        assertThat(props).containsKeys("label", "value");
        assertThat(((Map<String, Object>) props.get("label")).get("type")).isEqualTo("string");
    }

    @Test
    @SuppressWarnings("unchecked")
    void nestedRecordIsFullyInlined() throws Exception {
        var type = Wrapper.class.getDeclaredField("primary").getGenericType();
        var schema = InlineSchemaBuilder.typeToSchema(type);
        assertThat(schema.get("type")).isEqualTo("array");
        var items = (Map<String, Object>) schema.get("items");
        assertThat(items.get("type")).isEqualTo("object");
        var tagProps = (Map<String, Object>) items.get("properties");
        assertThat(tagProps).containsKeys("label", "value");

        String json = schema.toString();
        assertThat(json).doesNotContain("$ref");
        assertThat(json).doesNotContain("$defs");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildToolSchemaIncludesRequiredFields() throws Exception {
        var params = List.of(
                new ParameterDescriptor("msg", "String", true, "The message", null),
                new ParameterDescriptor("count", "int", false, "How many", null));

        var method       = TestHelper.class.getMethod("example", String.class, int.class);
        var methodParams = method.getParameters();

        var schema = InlineSchemaBuilder.buildToolSchema(params, methodParams);
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(schema.get("required")).isEqualTo(List.of("msg"));
        @SuppressWarnings("unchecked")
        var props = (Map<String, Object>) schema.get("properties");
        assertThat(props).containsKeys("msg", "count");
        @SuppressWarnings("unchecked")
        var msgSchema = (Map<String, Object>) props.get("msg");
        assertThat(msgSchema.get("description")).isEqualTo("The message");
    }

    @Test
    void bigDecimalMapsToNumberType() {
        var schema = InlineSchemaBuilder.typeToSchema(java.math.BigDecimal.class);
        assertThat(schema.get("type")).isEqualTo("number");
    }

    @Test
    void bigIntegerMapsToNumberType() {
        var schema = InlineSchemaBuilder.typeToSchema(java.math.BigInteger.class);
        assertThat(schema.get("type")).isEqualTo("number");
    }

    sealed interface Shape permits Circle, Square {}

    record Circle(double radius) implements Shape {}

    record Square(double side) implements Shape {}

    @Test
    @SuppressWarnings("unchecked")
    void sealedInterfaceProducesOneOf() {
        var schema = InlineSchemaBuilder.typeToSchema(Shape.class);
        assertThat(schema).containsKey("oneOf");
        var variants = (List<Map<String, Object>>) schema.get("oneOf");
        assertThat(variants).hasSize(2);
        assertThat(((Map<String, Object>) variants.get(0).get("properties"))).containsKey("radius");
        assertThat(((Map<String, Object>) variants.get(1).get("properties"))).containsKey("side");
    }


    public static class TestHelper {
        public static void example(String msg, int count) {}
    }
}
