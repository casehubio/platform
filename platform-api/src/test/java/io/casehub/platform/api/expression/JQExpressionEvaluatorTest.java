package io.casehub.platform.api.expression;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JQExpressionEvaluatorTest {

    @Test
    void type_returnsJq() {
        var eval = new JQExpressionEvaluator(".status");
        assertThat(eval.type()).isEqualTo("jq");
    }

    @Test
    void expression_returnsValue() {
        var eval = new JQExpressionEvaluator(".status == \"active\"");
        assertThat(eval.expression()).isEqualTo(".status == \"active\"");
    }

    @Test
    void implementsExpressionEvaluator() {
        var eval = new JQExpressionEvaluator(".x");
        assertThat(eval).isInstanceOf(ExpressionEvaluator.class);
    }

    @Test
    void rejectsNullExpression() {
        assertThatThrownBy(() -> new JQExpressionEvaluator(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equality_sameExpression() {
        var a = new JQExpressionEvaluator(".x");
        var b = new JQExpressionEvaluator(".x");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void inequality_differentExpression() {
        var a = new JQExpressionEvaluator(".x");
        var b = new JQExpressionEvaluator(".y");
        assertThat(a).isNotEqualTo(b);
    }
}
