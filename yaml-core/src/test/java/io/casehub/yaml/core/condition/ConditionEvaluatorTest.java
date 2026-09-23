package io.casehub.yaml.core.condition;

import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionEvaluatorTest {

    @Test
    void simpleTruthyValue_returnsTrue() {
        ConditionEvaluator evaluator = new ConditionEvaluator(null);
        assertThat(evaluator.evaluate("true")).isTrue();
    }

    @Test
    void simpleFalsyValue_returnsFalse() {
        ConditionEvaluator evaluator = new ConditionEvaluator(null);
        assertThat(evaluator.evaluate("false")).isFalse();
    }

    @Test
    void yesValue_returnsTrue() {
        ConditionEvaluator evaluator = new ConditionEvaluator(null);
        assertThat(evaluator.evaluate("yes")).isTrue();
    }

    @Test
    void noValue_returnsFalse() {
        ConditionEvaluator evaluator = new ConditionEvaluator(null);
        assertThat(evaluator.evaluate("no")).isFalse();
    }

    @Test
    void equalityExpression_delegatesToExpressionFunction() {
        Function<String, Boolean> exprDelegate = expr -> expr.trim().equals("HIGH == 'HIGH'");
        ConditionEvaluator evaluator = new ConditionEvaluator(exprDelegate);
        assertThat(evaluator.evaluate("HIGH == 'HIGH'")).isTrue();
    }

    @Test
    void nonBooleanWithoutDelegate_throwsConditionEvaluationException() {
        ConditionEvaluator evaluator = new ConditionEvaluator(null);
        assertThatThrownBy(() -> evaluator.evaluate("MEAN_REVERTING"))
                .isInstanceOf(ConditionEvaluationException.class)
                .hasMessageContaining("no expression engine configured");
    }

    @Test
    void expressionDelegate_receivesFullString() {
        String[] captured = {null};
        Function<String, Boolean> exprDelegate = expr -> {
            captured[0] = expr;
            return true;
        };
        ConditionEvaluator evaluator = new ConditionEvaluator(exprDelegate);
        evaluator.evaluate("${regime} == 'MEAN_REVERTING'");
        assertThat(captured[0]).isEqualTo("${regime} == 'MEAN_REVERTING'");
    }

    @Test
    void expressionDelegate_throwsException_propagates() {
        Function<String, Boolean> exprDelegate = expr -> {
            throw new RuntimeException("eval failed");
        };
        ConditionEvaluator evaluator = new ConditionEvaluator(exprDelegate);
        assertThatThrownBy(() -> evaluator.evaluate("bad expression"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("eval failed");
    }
}
