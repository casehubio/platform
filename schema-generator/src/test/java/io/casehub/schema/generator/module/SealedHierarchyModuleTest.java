package io.casehub.schema.generator.module;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SealedHierarchyModuleTest {

    sealed interface Shape permits Circle, Rectangle, Triangle {}
    record Circle(double radius) implements Shape {}
    record Rectangle(double width, double height) implements Shape {}
    record Triangle(double base, double height) implements Shape {}

    sealed interface Transport permits Car, Bicycle {}
    record Car(int seats, String fuel) implements Transport {}
    record Bicycle(boolean electric) implements Transport {}

    record PlainRecord(String name) {}

    private SchemaGenerator generator(Map<Class<?>, Map<Class<?>, String>> overrides) {
        var builder = new SchemaGeneratorConfigBuilder(
            SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
        builder.with(Option.DEFINITIONS_FOR_ALL_OBJECTS);
        builder.with(new SealedHierarchyModule(overrides));
        return new SchemaGenerator(builder.build());
    }

    private SchemaGenerator generator() {
        return generator(Map.of());
    }

    private List<String> extractDiscriminatorValues(JsonNode schema) {
        var values = new ArrayList<String>();
        var oneOf = schema.get("oneOf");
        if (oneOf != null) {
            for (var entry : oneOf) {
                var props = entry.get("properties");
                if (props != null && props.has("type")) {
                    var constNode = props.get("type").get("const");
                    if (constNode != null) {
                        values.add(constNode.asText());
                    }
                }
            }
        }
        return values;
    }

    @Test
    void sealedInterface_generatesOneOf_withTypeDiscriminator() {
        var schema = generator().generateSchema(Shape.class);

        assertThat(schema.has("oneOf")).isTrue();
        assertThat(schema.get("oneOf").size()).isEqualTo(3);
    }

    @Test
    void discriminatorValues_areLowerCamelCase_byDefault() {
        var schema = generator().generateSchema(Shape.class);

        var values = extractDiscriminatorValues(schema);
        assertThat(values).containsExactlyInAnyOrder("circle", "rectangle", "triangle");
    }

    @Test
    void discriminatorOverrides_replaceDefaultValues() {
        var overrides = Map.<Class<?>, Map<Class<?>, String>>of(
            Shape.class, Map.of(Circle.class, "round-shape")
        );
        var schema = generator(overrides).generateSchema(Shape.class);

        var values = extractDiscriminatorValues(schema);
        assertThat(values).contains("round-shape");
        assertThat(values).doesNotContain("circle");
    }

    @Test
    void nonSealedType_isNotIntercepted() {
        var schema = generator().generateSchema(PlainRecord.class);
        assertThat(schema.has("oneOf")).isFalse();
    }

    @Test
    void secondSealedInterface_generatesCorrectVariantCount() {
        var schema = generator().generateSchema(Transport.class);

        assertThat(schema.has("oneOf")).isTrue();
        assertThat(schema.get("oneOf").size()).isEqualTo(2);
    }

    @Test
    void eachOneOfEntry_hasDiscriminatorAndRef() {
        var schema = generator().generateSchema(Shape.class);

        for (var entry : schema.get("oneOf")) {
            assertThat(entry.has("properties")).isTrue();
            assertThat(entry.get("properties").has("type")).isTrue();
            assertThat(entry.get("properties").get("type").has("const")).isTrue();
            assertThat(entry.has("required")).isTrue();
            assertThat(entry.has("$ref")).isTrue();
        }
    }

    @Test
    void defaultDiscriminator_lowerCamelCase() {
        assertThat(SealedHierarchyModule.defaultDiscriminator(Circle.class))
            .isEqualTo("circle");
        assertThat(SealedHierarchyModule.defaultDiscriminator(Rectangle.class))
            .isEqualTo("rectangle");
        assertThat(SealedHierarchyModule.defaultDiscriminator(Car.class))
            .isEqualTo("car");
    }
}
