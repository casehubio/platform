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

    @Test
    void canAccept_same_type_always_true() {
        for (ParameterType type : ParameterType.values()) {
            assertThat(type.canAccept(type))
                    .as(type + " should accept itself")
                    .isTrue();
        }
    }

    @Test
    void canAccept_string_accepts_scalars() {
        assertThat(ParameterType.STRING.canAccept(ParameterType.INTEGER)).isTrue();
        assertThat(ParameterType.STRING.canAccept(ParameterType.NUMBER)).isTrue();
        assertThat(ParameterType.STRING.canAccept(ParameterType.BOOLEAN)).isTrue();
    }

    @Test
    void canAccept_string_rejects_list() {
        assertThat(ParameterType.STRING.canAccept(ParameterType.LIST)).isFalse();
    }

    @Test
    void canAccept_number_accepts_integer() {
        assertThat(ParameterType.NUMBER.canAccept(ParameterType.INTEGER)).isTrue();
    }

    @Test
    void canAccept_number_rejects_others() {
        assertThat(ParameterType.NUMBER.canAccept(ParameterType.STRING)).isFalse();
        assertThat(ParameterType.NUMBER.canAccept(ParameterType.BOOLEAN)).isFalse();
        assertThat(ParameterType.NUMBER.canAccept(ParameterType.LIST)).isFalse();
    }

    @Test
    void canAccept_integer_rejects_number() {
        assertThat(ParameterType.INTEGER.canAccept(ParameterType.NUMBER)).isFalse();
    }

    @Test
    void canAccept_boolean_rejects_non_boolean() {
        assertThat(ParameterType.BOOLEAN.canAccept(ParameterType.STRING)).isFalse();
        assertThat(ParameterType.BOOLEAN.canAccept(ParameterType.INTEGER)).isFalse();
        assertThat(ParameterType.BOOLEAN.canAccept(ParameterType.NUMBER)).isFalse();
        assertThat(ParameterType.BOOLEAN.canAccept(ParameterType.LIST)).isFalse();
    }

    @Test
    void canAccept_list_rejects_non_list() {
        assertThat(ParameterType.LIST.canAccept(ParameterType.STRING)).isFalse();
        assertThat(ParameterType.LIST.canAccept(ParameterType.INTEGER)).isFalse();
        assertThat(ParameterType.LIST.canAccept(ParameterType.NUMBER)).isFalse();
        assertThat(ParameterType.LIST.canAccept(ParameterType.BOOLEAN)).isFalse();
    }

}
