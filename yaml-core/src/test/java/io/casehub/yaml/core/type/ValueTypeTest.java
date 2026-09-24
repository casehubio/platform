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
}
