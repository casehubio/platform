package io.casehub.yaml.core.condition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TruthinessTest {

    @Test
    void strict_true_returns_true() {
        assertThat(Truthiness.isTruthy("true")).isTrue();
    }

    @Test
    void strict_false_returns_false() {
        assertThat(Truthiness.isTruthy("false")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"True", "TRUE", "yes", "Yes", "on", "ON", "y", "Y", "1", "no", "No", "off", "OFF", "n", "N", "0", "False", "FALSE"})
    void norway_and_case_variants_throw(String value) {
        assertThatThrownBy(() -> Truthiness.isTruthy(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a boolean literal");
    }

    @Test
    void invalid_value_throws() {
        assertThatThrownBy(() -> Truthiness.isTruthy("production"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("production")
                .hasMessageContaining("not a boolean literal");
    }

    @Test
    void empty_string_throws() {
        assertThatThrownBy(() -> Truthiness.isTruthy(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
