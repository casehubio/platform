package io.casehub.platform.api.expression;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MvelExpressionEvaluatorTest {

    @Test
    void type_returnsMvel() {
        var eval = new MvelExpressionEvaluator("status == \"active\"");
        assertThat(eval.type()).isEqualTo("mvel");
    }

    @Test
    void expression_returnsValue() {
        var eval = new MvelExpressionEvaluator("age > 20");
        assertThat(eval.expression()).isEqualTo("age > 20");
    }

    @Test
    void implementsExpressionEvaluator() {
        var eval = new MvelExpressionEvaluator("x");
        assertThat(eval).isInstanceOf(ExpressionEvaluator.class);
    }

    @Test
    void rejectsNullExpression() {
        assertThatThrownBy(() -> new MvelExpressionEvaluator(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equality_sameExpression() {
        var a = new MvelExpressionEvaluator("x > 1");
        var b = new MvelExpressionEvaluator("x > 1");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
