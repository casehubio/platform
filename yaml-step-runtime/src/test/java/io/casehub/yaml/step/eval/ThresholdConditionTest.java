package io.casehub.yaml.step.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThresholdConditionTest {

    @ParameterizedTest
    @CsvSource({
            "'14 supply',       '>=', 14.0, 'supply'",
            "'>=14 supply',     '>=', 14.0, 'supply'",
            "'>14 supply',      '>',  14.0, 'supply'",
            "'<22 temperature', '<',  22.0, 'temperature'",
            "'<=80 allocation', '<=', 80.0, 'allocation'",
            "'3.14 pi',         '>=', 3.14, 'pi'"
    })
    void parsesConditionString(String input, String expectedOp,
                                double expectedThreshold, String expectedMetric) {
        var cond = ThresholdCondition.parse(input);
        assertThat(cond.operator()).isEqualTo(expectedOp);
        assertThat(cond.threshold()).isCloseTo(expectedThreshold, org.assertj.core.data.Offset.offset(0.001));
        assertThat(cond.metricName()).isEqualTo(expectedMetric);
    }

    @Test
    void evaluateGreaterOrEqual() {
        var cond = ThresholdCondition.parse(">=14 supply");
        assertThat(cond.test(14.0)).isTrue();
        assertThat(cond.test(15.0)).isTrue();
        assertThat(cond.test(13.9)).isFalse();
    }

    @Test
    void evaluateGreaterThan() {
        var cond = ThresholdCondition.parse(">14 supply");
        assertThat(cond.test(14.0)).isFalse();
        assertThat(cond.test(14.1)).isTrue();
    }

    @Test
    void evaluateLessThan() {
        var cond = ThresholdCondition.parse("<22 temperature");
        assertThat(cond.test(21.0)).isTrue();
        assertThat(cond.test(22.0)).isFalse();
        assertThat(cond.test(23.0)).isFalse();
    }

    @Test
    void evaluateLessOrEqual() {
        var cond = ThresholdCondition.parse("<=80 allocation");
        assertThat(cond.test(80.0)).isTrue();
        assertThat(cond.test(80.1)).isFalse();
    }

    @Test
    void invalidInputThrows() {
        assertThatThrownBy(() -> ThresholdCondition.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ThresholdCondition.parse("nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void spaceBetweenOperatorAndNumber() {
        var cond = ThresholdCondition.parse(">= 14 supply");
        assertThat(cond.operator()).isEqualTo(">=");
        assertThat(cond.threshold()).isEqualTo(14.0);
        assertThat(cond.metricName()).isEqualTo("supply");
    }
}
