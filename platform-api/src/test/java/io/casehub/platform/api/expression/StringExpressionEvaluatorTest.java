package io.casehub.platform.api.expression;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StringExpressionEvaluatorTest {

    @Test
    void jqExpressionEvaluatorIsStringExpressionEvaluator() {
        ExpressionEvaluator eval = new JQExpressionEvaluator(".data.orderId");
        assertThat(eval).isInstanceOf(StringExpressionEvaluator.class);
        assertThat(((StringExpressionEvaluator) eval).expression()).isEqualTo(".data.orderId");
    }

    @Test
    void mvelExpressionEvaluatorIsStringExpressionEvaluator() {
        ExpressionEvaluator eval = new MvelExpressionEvaluator("data.severity >= 3");
        assertThat(eval).isInstanceOf(StringExpressionEvaluator.class);
        assertThat(((StringExpressionEvaluator) eval).expression()).isEqualTo("data.severity >= 3");
    }

    @Test
    void lambdaExpressionIsNotStringExpressionEvaluator() {
        ExpressionEvaluator eval = new LambdaExpression<>(x -> x);
        assertThat(eval).isNotInstanceOf(StringExpressionEvaluator.class);
    }
}
