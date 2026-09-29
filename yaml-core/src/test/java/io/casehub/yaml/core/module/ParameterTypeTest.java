package io.casehub.yaml.core.module;

import io.casehub.yaml.plugin.api.ParameterType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParameterTypeTest {

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
    void canAccept_string_rejects_array() {
        assertThat(ParameterType.STRING.canAccept(ParameterType.ARRAY)).isFalse();
    }

    @Test
    void canAccept_number_accepts_integer() {
        assertThat(ParameterType.NUMBER.canAccept(ParameterType.INTEGER)).isTrue();
    }

    @Test
    void canAccept_number_rejects_others() {
        assertThat(ParameterType.NUMBER.canAccept(ParameterType.STRING)).isFalse();
        assertThat(ParameterType.NUMBER.canAccept(ParameterType.BOOLEAN)).isFalse();
        assertThat(ParameterType.NUMBER.canAccept(ParameterType.ARRAY)).isFalse();
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
        assertThat(ParameterType.BOOLEAN.canAccept(ParameterType.ARRAY)).isFalse();
    }

    @Test
    void canAccept_array_rejects_non_array() {
        assertThat(ParameterType.ARRAY.canAccept(ParameterType.STRING)).isFalse();
        assertThat(ParameterType.ARRAY.canAccept(ParameterType.INTEGER)).isFalse();
        assertThat(ParameterType.ARRAY.canAccept(ParameterType.NUMBER)).isFalse();
        assertThat(ParameterType.ARRAY.canAccept(ParameterType.BOOLEAN)).isFalse();
    }

    @Test
    void fromString_standard_names() {
        assertThat(ParameterType.fromString("string")).isEqualTo(ParameterType.STRING);
        assertThat(ParameterType.fromString("integer")).isEqualTo(ParameterType.INTEGER);
        assertThat(ParameterType.fromString("number")).isEqualTo(ParameterType.NUMBER);
        assertThat(ParameterType.fromString("boolean")).isEqualTo(ParameterType.BOOLEAN);
        assertThat(ParameterType.fromString("list")).isEqualTo(ParameterType.ARRAY);
        assertThat(ParameterType.fromString("array")).isEqualTo(ParameterType.ARRAY);
    }

    @Test
    void fromString_decimal_alias_for_number() {
        assertThat(ParameterType.fromString("decimal")).isEqualTo(ParameterType.NUMBER);
        assertThat(ParameterType.fromString("DECIMAL")).isEqualTo(ParameterType.NUMBER);
    }

    @Test
    void fromString_case_insensitive() {
        assertThat(ParameterType.fromString("STRING")).isEqualTo(ParameterType.STRING);
        assertThat(ParameterType.fromString("Integer")).isEqualTo(ParameterType.INTEGER);
        assertThat(ParameterType.fromString("Boolean")).isEqualTo(ParameterType.BOOLEAN);
    }

    @Test
    void fromString_unknown_throws() {
        assertThatThrownBy(() -> ParameterType.fromString("blob"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blob");
    }

    @Test
    void valueType_toParameterType_maps_all_scalars() {
        assertThat(io.casehub.yaml.core.type.ValueType.STRING.toParameterType()).isEqualTo(ParameterType.STRING);
        assertThat(io.casehub.yaml.core.type.ValueType.INTEGER.toParameterType()).isEqualTo(ParameterType.INTEGER);
        assertThat(io.casehub.yaml.core.type.ValueType.NUMBER.toParameterType()).isEqualTo(ParameterType.NUMBER);
        assertThat(io.casehub.yaml.core.type.ValueType.BOOLEAN.toParameterType()).isEqualTo(ParameterType.BOOLEAN);
    }
}
