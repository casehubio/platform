package io.casehub.yaml.core.type;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueTypeTest {

    @Test
    void parse_string_returns_same_value() {
        assertThat(ValueType.STRING.parse("hello")).isEqualTo("hello");
    }

    @Test
    void parse_integer_returns_int() {
        assertThat(ValueType.INTEGER.parse("8080")).isEqualTo(8080);
    }

    @Test
    void parse_boolean_true_via_truthiness() {
        assertThat(ValueType.BOOLEAN.parse("true")).isEqualTo(true);
        assertThat(ValueType.BOOLEAN.parse("yes")).isEqualTo(true);
        assertThat(ValueType.BOOLEAN.parse("1")).isEqualTo(true);
    }

    @Test
    void parse_boolean_false_via_truthiness() {
        assertThat(ValueType.BOOLEAN.parse("false")).isEqualTo(false);
        assertThat(ValueType.BOOLEAN.parse("no")).isEqualTo(false);
    }

    @Test
    void parse_number_returns_double() {
        assertThat(ValueType.NUMBER.parse("3.14")).isEqualTo(3.14);
    }

    @Test
    void parse_integer_invalid_throws() {
        assertThatThrownBy(() -> ValueType.INTEGER.parse("abc"))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void parse_number_invalid_throws() {
        assertThatThrownBy(() -> ValueType.NUMBER.parse("xyz"))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void accepts_integer_java_types() {
        assertThat(ValueType.INTEGER.accepts("int")).isTrue();
        assertThat(ValueType.INTEGER.accepts("java.lang.Integer")).isTrue();
        assertThat(ValueType.INTEGER.accepts("long")).isTrue();
        assertThat(ValueType.INTEGER.accepts("java.lang.Long")).isTrue();
        assertThat(ValueType.INTEGER.accepts("java.lang.Number")).isTrue();
        assertThat(ValueType.INTEGER.accepts("java.lang.String")).isFalse();
    }

    @Test
    void accepts_string_java_types() {
        assertThat(ValueType.STRING.accepts("java.lang.String")).isTrue();
        assertThat(ValueType.STRING.accepts("java.lang.CharSequence")).isTrue();
        assertThat(ValueType.STRING.accepts("int")).isFalse();
    }

    @Test
    void accepts_boolean_java_types() {
        assertThat(ValueType.BOOLEAN.accepts("boolean")).isTrue();
        assertThat(ValueType.BOOLEAN.accepts("java.lang.Boolean")).isTrue();
    }

    @Test
    void accepts_number_java_types() {
        assertThat(ValueType.NUMBER.accepts("double")).isTrue();
        assertThat(ValueType.NUMBER.accepts("java.lang.Double")).isTrue();
        assertThat(ValueType.NUMBER.accepts("float")).isTrue();
        assertThat(ValueType.NUMBER.accepts("java.math.BigDecimal")).isTrue();
        assertThat(ValueType.NUMBER.accepts("java.lang.Number")).isTrue();
    }

    @Test
    void fromString_resolves_all_scalar_types() {
        assertThat(ValueType.fromString("STRING")).isEqualTo(ValueType.STRING);
        assertThat(ValueType.fromString("INTEGER")).isEqualTo(ValueType.INTEGER);
        assertThat(ValueType.fromString("NUMBER")).isEqualTo(ValueType.NUMBER);
        assertThat(ValueType.fromString("BOOLEAN")).isEqualTo(ValueType.BOOLEAN);
    }

    @Test
    void fromString_is_case_insensitive() {
        assertThat(ValueType.fromString("string")).isEqualTo(ValueType.STRING);
        assertThat(ValueType.fromString("Integer")).isEqualTo(ValueType.INTEGER);
        assertThat(ValueType.fromString("boolean")).isEqualTo(ValueType.BOOLEAN);
    }

    @Test
    void fromString_accepts_decimal_alias() {
        assertThat(ValueType.fromString("DECIMAL")).isEqualTo(ValueType.NUMBER);
        assertThat(ValueType.fromString("decimal")).isEqualTo(ValueType.NUMBER);
    }

    @Test
    void fromString_returns_null_for_unknown() {
        assertThat(ValueType.fromString("ARRAY")).isNull();
        assertThat(ValueType.fromString("LIST")).isNull();
        assertThat(ValueType.fromString("OBJECT")).isNull();
        assertThat(ValueType.fromString("map")).isNull();
    }

    @Test
    void validate_string() {
        assertThat(ValueType.STRING.validate("hello")).isTrue();
        assertThat(ValueType.STRING.validate(42)).isFalse();
        assertThat(ValueType.STRING.validate(null)).isFalse();
    }

    @Test
    void validate_integer() {
        assertThat(ValueType.INTEGER.validate(42)).isTrue();
        assertThat(ValueType.INTEGER.validate(42L)).isTrue();
        assertThat(ValueType.INTEGER.validate(3.14)).isFalse();
        assertThat(ValueType.INTEGER.validate("42")).isFalse();
    }

    @Test
    void validate_number() {
        assertThat(ValueType.NUMBER.validate(3.14)).isTrue();
        assertThat(ValueType.NUMBER.validate(42)).isTrue();
        assertThat(ValueType.NUMBER.validate(42L)).isTrue();
        assertThat(ValueType.NUMBER.validate("3.14")).isFalse();
    }

    @Test
    void validate_boolean() {
        assertThat(ValueType.BOOLEAN.validate(true)).isTrue();
        assertThat(ValueType.BOOLEAN.validate(false)).isTrue();
        assertThat(ValueType.BOOLEAN.validate("true")).isFalse();
        assertThat(ValueType.BOOLEAN.validate(1)).isFalse();
    }
}
