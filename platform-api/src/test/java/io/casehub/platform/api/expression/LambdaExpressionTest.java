package io.casehub.platform.api.expression;

import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LambdaExpressionTest {

    @Test
    void eval_delegatesToFunction() {
        var expr = new LambdaExpression<>(String::length);
        assertThat(expr.eval("hello")).isEqualTo(5);
    }

    @Test
    void type_returnsLambda() {
        var expr = new LambdaExpression<>(Function.identity());
        assertThat(expr.type()).isEqualTo("lambda");
    }

    @Test
    void implementsBothInterfaces() {
        var expr = new LambdaExpression<>(String::length);
        assertThat(expr).isInstanceOf(ExpressionEvaluator.class);
        assertThat(expr).isInstanceOf(CompiledExpression.class);
    }

    @Test
    void booleanPredicate_worksNaturally() {
        LambdaExpression<Integer, Boolean> isPositive =
                new LambdaExpression<>(n -> n > 0);
        assertThat(isPositive.eval(5)).isTrue();
        assertThat(isPositive.eval(-1)).isFalse();
    }

    @Test
    void rejectsNullFunction() {
        assertThatThrownBy(() -> new LambdaExpression<>(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void transformation_notJustBoolean() {
        LambdaExpression<String, String> upper =
                new LambdaExpression<>(String::toUpperCase);
        assertThat(upper.eval("hello")).isEqualTo("HELLO");
    }
}
