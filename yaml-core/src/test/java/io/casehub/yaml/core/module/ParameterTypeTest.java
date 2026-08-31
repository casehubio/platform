package io.casehub.yaml.core.module;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParameterTypeTest {

    @Test
    void string_returns_value() {
        assertThat(ParameterType.STRING.parse("hello")).isEqualTo("hello");
    }

    @Test
    void list_splits_on_comma() {
        assertThat(ParameterType.LIST.parse("a, b, c")).isEqualTo(List.of("a", "b", "c"));
    }

    @Test
    void list_single_value() {
        assertThat(ParameterType.LIST.parse("only")).isEqualTo(List.of("only"));
    }

    @Test
    void integer_parses() {
        assertThat(ParameterType.INTEGER.parse("42")).isEqualTo(42);
    }

    @Test
    void integer_invalid_throws() {
        assertThatThrownBy(() -> ParameterType.INTEGER.parse("abc"))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void number_parses() {
        assertThat(ParameterType.NUMBER.parse("3.14")).isEqualTo(3.14);
    }

    @Test
    void number_invalid_throws() {
        assertThatThrownBy(() -> ParameterType.NUMBER.parse("abc"))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void boolean_parses_truthy() {
        assertThat(ParameterType.BOOLEAN.parse("yes")).isEqualTo(true);
    }

    @Test
    void boolean_parses_falsy() {
        assertThat(ParameterType.BOOLEAN.parse("no")).isEqualTo(false);
    }
}
