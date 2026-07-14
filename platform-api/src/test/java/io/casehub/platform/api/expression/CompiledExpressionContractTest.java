package io.casehub.platform.api.expression;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompiledExpressionContractTest {

    @Test
    void eval_returnsResultFromFunction() {
        CompiledExpression<String, Integer> expr = new CompiledExpression<>() {
            @Override
            public String type() { return "test"; }

            @Override
            public Integer eval(String context) { return context.length(); }
        };
        assertThat(expr.eval("hello")).isEqualTo(5);
        assertThat(expr.type()).isEqualTo("test");
    }

    @Test
    void eval_booleanResult() {
        CompiledExpression<Integer, Boolean> expr = new CompiledExpression<>() {
            @Override
            public String type() { return "test"; }

            @Override
            public Boolean eval(Integer context) { return context > 0; }
        };
        assertThat(expr.eval(5)).isTrue();
        assertThat(expr.eval(-1)).isFalse();
    }
}
