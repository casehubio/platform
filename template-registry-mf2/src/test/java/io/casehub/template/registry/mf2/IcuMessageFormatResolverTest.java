package io.casehub.template.registry.mf2;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IcuMessageFormatResolverTest {

    private final IcuMessageFormatResolver resolver = new IcuMessageFormatResolver();

    @Test
    void compileParsesValidPattern() {
        var compiled = resolver.compile(
            ".input {$count :integer}\n.match $count\n1 {{{$count} item}}\n* {{{$count} items}}",
            "en");
        assertThat(compiled).isNotNull();
    }

    @Test
    void compileThrowsOnInvalidLanguageTag() {
        assertThatThrownBy(() -> resolver.compile("Hello, {$name}!", "@@"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid BCP 47");
    }

    @Test
    void formatPluralEnglishSingular() {
        var compiled = resolver.compile(
            ".input {$count :integer}\n.match $count\n1 {{{$count} item}}\n* {{{$count} items}}",
            "en");
        String result = resolver.format(compiled, Map.of("count", "1"));
        assertThat(result).isEqualTo("1 item");
    }

    @Test
    void formatPluralEnglishPlural() {
        var compiled = resolver.compile(
            ".input {$count :integer}\n.match $count\n1 {{{$count} item}}\n* {{{$count} items}}",
            "en");
        String result = resolver.format(compiled, Map.of("count", "5"));
        assertThat(result).isEqualTo("5 items");
    }

    @Test
    void formatGenderSelection() {
        var compiled = resolver.compile(
            ".input {$gender :string}\n.match $gender\nmasculine {{He joined}}\nfeminine {{She joined}}\n* {{They joined}}",
            "en");
        assertThat(resolver.format(compiled, Map.of("gender", "masculine")))
            .isEqualTo("He joined");
        assertThat(resolver.format(compiled, Map.of("gender", "feminine")))
            .isEqualTo("She joined");
        assertThat(resolver.format(compiled, Map.of("gender", "other")))
            .isEqualTo("They joined");
    }

    @Test
    void formatSimpleMessage() {
        var compiled = resolver.compile("Hello, {$name}!", "en");
        String result = resolver.format(compiled, Map.of("name", "World"));
        assertThat(result).isEqualTo("Hello, World!");
    }

    @Test
    void extractSchemaReturnsSelectors() {
        var schema = resolver.extractSchema(
            ".input {$count :integer}\n.match $count\n1 {{{$count} item}}\n* {{{$count} items}}");
        assertThat(schema).isNotNull();
        assertThat(schema.pattern()).contains(".match");
        assertThat(schema.selectors()).isNotEmpty();
        assertThat(schema.variantKeys()).contains("1", "*");
    }

    @Test
    void formatFrenchPlural() {
        var compiled = resolver.compile(
            ".input {$count :integer}\n.match $count\n1 {{{$count} élément}}\n* {{{$count} éléments}}",
            "fr");
        assertThat(resolver.format(compiled, Map.of("count", "1")))
            .isEqualTo("1 élément");
        assertThat(resolver.format(compiled, Map.of("count", "5")))
            .isEqualTo("5 éléments");
    }
}
