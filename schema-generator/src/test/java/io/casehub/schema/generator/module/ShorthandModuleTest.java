package io.casehub.schema.generator.module;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ShorthandModuleTest {

    record Price(double amount, String currency) {}
    record Duration(int value, String unit) {}
    record PlainType(String name) {}

    sealed interface Animal permits Dog, Cat {}
    record Dog(String breed) implements Animal {}
    record Cat(boolean indoor) implements Animal {}

    private SchemaGenerator generator(Map<Class<?>, ShorthandDefinition> defs) {
        var builder = new SchemaGeneratorConfigBuilder(
            SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
        builder.with(Option.DEFINITIONS_FOR_ALL_OBJECTS);
        builder.with(new ShorthandModule(defs));
        return new SchemaGenerator(builder.build());
    }

    @Test
    void shorthandType_generatesOneOf_withScalarAndObjectForms() {
        var gen = generator(Map.of(
            Price.class, ShorthandDefinition.of(
                config -> {
                    ObjectNode s = config.createObjectNode();
                    s.put("type", "number");
                    return s;
                },
                config -> {
                    ObjectNode o = config.createObjectNode();
                    o.put("type", "object");
                    o.putObject("properties").putObject("amount").put("type", "number");
                    return o;
                }
            )
        ));
        var schema = gen.generateSchema(Price.class);

        assertThat(schema.has("oneOf")).isTrue();
        assertThat(schema.get("oneOf").size()).isEqualTo(2);
    }

    @Test
    void scalarForm_matchesCallerProvidedSchema() {
        var gen = generator(Map.of(
            Price.class, ShorthandDefinition.of(
                config -> {
                    ObjectNode s = config.createObjectNode();
                    s.put("type", "number").put("minimum", 0);
                    return s;
                },
                config -> {
                    ObjectNode o = config.createObjectNode();
                    o.put("type", "object");
                    return o;
                }
            )
        ));
        var schema = gen.generateSchema(Price.class);

        for (var option : schema.get("oneOf")) {
            if ("number".equals(option.path("type").asText())) {
                assertThat(option.get("minimum").asInt()).isEqualTo(0);
                return;
            }
        }
        org.junit.jupiter.api.Assertions.fail("No number form found");
    }

    @Test
    void objectForm_matchesCallerProvidedSchema() {
        var gen = generator(Map.of(
            Price.class, ShorthandDefinition.of(
                config -> config.createObjectNode().put("type", "string"),
                config -> {
                    ObjectNode o = config.createObjectNode();
                    o.put("type", "object");
                    ObjectNode props = o.putObject("properties");
                    props.putObject("amount").put("type", "number");
                    props.putObject("currency").put("type", "string");
                    o.putArray("required").add("amount").add("currency");
                    return o;
                }
            )
        ));
        var schema = gen.generateSchema(Price.class);

        for (var option : schema.get("oneOf")) {
            if ("object".equals(option.path("type").asText())) {
                assertThat(option.get("properties").has("amount")).isTrue();
                assertThat(option.get("properties").has("currency")).isTrue();
                assertThat(option.get("required").toString()).contains("amount");
                return;
            }
        }
        org.junit.jupiter.api.Assertions.fail("No object form found");
    }

    @Test
    void nonShorthandType_isNotIntercepted() {
        var gen = generator(Map.of(
            Price.class, ShorthandDefinition.of(
                config -> config.createObjectNode().put("type", "number"),
                config -> config.createObjectNode().put("type", "object")
            )
        ));
        var schema = gen.generateSchema(PlainType.class);

        assertThat(schema.has("oneOf")).isFalse();
    }

    @Test
    void multipleShorthandTypes_inOneModule() {
        var gen = generator(Map.of(
            Price.class, ShorthandDefinition.of(
                config -> config.createObjectNode().put("type", "number"),
                config -> config.createObjectNode().put("type", "object")
            ),
            Duration.class, ShorthandDefinition.of(
                config -> config.createObjectNode().put("type", "string"),
                config -> config.createObjectNode().put("type", "object")
            )
        ));

        var priceSchema = gen.generateSchema(Price.class);
        var durationSchema = gen.generateSchema(Duration.class);

        assertThat(priceSchema.has("oneOf")).isTrue();
        assertThat(durationSchema.has("oneOf")).isTrue();

        boolean priceHasNumber = false;
        for (var opt : priceSchema.get("oneOf")) {
            if ("number".equals(opt.path("type").asText())) priceHasNumber = true;
        }
        assertThat(priceHasNumber).isTrue();

        boolean durationHasString = false;
        for (var opt : durationSchema.get("oneOf")) {
            if ("string".equals(opt.path("type").asText())) durationHasString = true;
        }
        assertThat(durationHasString).isTrue();
    }

    @Test
    void shorthandAndSealedHierarchy_coexist() {
        var builder = new SchemaGeneratorConfigBuilder(
            SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
        builder.with(Option.DEFINITIONS_FOR_ALL_OBJECTS);
        builder.with(new SealedHierarchyModule());
        builder.with(new ShorthandModule(Map.of(
            Price.class, ShorthandDefinition.of(
                config -> config.createObjectNode().put("type", "number"),
                config -> config.createObjectNode().put("type", "object")
            )
        )));
        var gen = new SchemaGenerator(builder.build());

        var priceSchema = gen.generateSchema(Price.class);
        assertThat(priceSchema.has("oneOf")).isTrue();
        assertThat(priceSchema.get("oneOf").size()).isEqualTo(2);

        var animalSchema = gen.generateSchema(Animal.class);
        assertThat(animalSchema.has("oneOf")).isTrue();
        assertThat(animalSchema.get("oneOf").size()).isEqualTo(2);
        for (var entry : animalSchema.get("oneOf")) {
            assertThat(entry.has("properties")).isTrue();
            assertThat(entry.get("properties").has("type")).isTrue();
        }
    }
}
