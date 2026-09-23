package io.casehub.yaml.core.condition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

// --- Runtime: ConditionEvaluator integration with Truthiness ---

    @Test
    void conditionEvaluator_delegatesToTruthiness_forBooleanStrings() {
        var evaluator = new ConditionEvaluator(null);
        assertThat(evaluator.evaluate("true")).isTrue();
        assertThat(evaluator.evaluate("false")).isFalse();
        assertThat(evaluator.evaluate("yes")).isTrue();
        assertThat(evaluator.evaluate("no")).isFalse();
        assertThat(evaluator.evaluate("on")).isTrue();
        assertThat(evaluator.evaluate("off")).isFalse();
        assertThat(evaluator.evaluate("1")).isTrue();
        assertThat(evaluator.evaluate("0")).isFalse();
    }

    @Test
    void conditionEvaluator_caseInsensitive_viaTruthiness() {
        var evaluator = new ConditionEvaluator(null);
        assertThat(evaluator.evaluate("TRUE")).isTrue();
        assertThat(evaluator.evaluate("False")).isFalse();
        assertThat(evaluator.evaluate("YES")).isTrue();
    }

    @Test
    void conditionEvaluator_nonBoolean_delegatesToExpression() {
        var evaluator = new ConditionEvaluator(expr -> expr.contains("HIGH"));
        assertThat(evaluator.evaluate("risk == 'HIGH'")).isTrue();
        assertThat(evaluator.evaluate("risk == 'LOW'")).isFalse();
    }

    @Test
    void conditionEvaluator_resolvedVariable_evaluatesAsTruthiness() {
        // Simulates: when: ${enabled} where ${enabled} resolved to "yes"
        var evaluator = new ConditionEvaluator(null);
        assertThat(evaluator.evaluate("yes")).isTrue();
    }
}
