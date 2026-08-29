package io.casehub.yaml.core.condition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TruthinessTest {

    @ParameterizedTest
    @ValueSource(strings = {"true", "True", "TRUE", "yes", "Yes", "on", "ON", "y", "Y", "1"})
    void truthy_values(String value) {
        assertThat(Truthiness.isTruthy(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "False", "FALSE", "no", "No", "off", "OFF", "n", "N", "0"})
    void falsy_values(String value) {
        assertThat(Truthiness.isTruthy(value)).isFalse();
    }

    @Test
    void invalid_value_throws() {
        assertThatThrownBy(() -> Truthiness.isTruthy("production"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("production")
                .hasMessageContaining("not a boolean");
    }

    @Test
    void empty_string_throws() {
        assertThatThrownBy(() -> Truthiness.isTruthy(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
