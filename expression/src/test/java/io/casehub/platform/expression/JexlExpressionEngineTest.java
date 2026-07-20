package io.casehub.platform.expression;

import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionCompilationException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JexlExpressionEngineTest {

    private final JexlExpressionEngine engine = new JexlExpressionEngine();

    @SuppressWarnings("unchecked")
    private static final Class<Map<String, Object>> MAP_TYPE =
            (Class<Map<String, Object>>) (Class<?>) Map.class;

    @Test
    void type_returnsJexl() {
        assertThat(engine.type()).isEqualTo("jexl");
    }

    @Test
    void compile_booleanExpression() {
        CompiledExpression<Map<String, Object>, Boolean> expr =
                engine.compile("age > 20", MAP_TYPE, Boolean.class);
        assertThat(expr.eval(Map.of("age", 25))).isTrue();
        assertThat(expr.eval(Map.of("age", 15))).isFalse();
    }

    @Test
    void compile_stringEquality() {
        CompiledExpression<Map<String, Object>, Boolean> expr =
                engine.compile("name == 'Alice'", MAP_TYPE, Boolean.class);
        assertThat(expr.eval(Map.of("name", "Alice"))).isTrue();
        assertThat(expr.eval(Map.of("name", "Bob"))).isFalse();
    }

    @Test
    void compile_booleanLogic() {
        CompiledExpression<Map<String, Object>, Boolean> expr =
                engine.compile("age > 18 && name != 'Bob'", MAP_TYPE, Boolean.class);
        assertThat(expr.eval(Map.of("age", 25, "name", "Alice"))).isTrue();
        assertThat(expr.eval(Map.of("age", 25, "name", "Bob"))).isFalse();
        assertThat(expr.eval(Map.of("age", 16, "name", "Alice"))).isFalse();
    }

    @Test
    void compile_withVariables() {
        CompiledExpression<Map<String, Object>, Boolean> expr =
                engine.compile("score < threshold", MAP_TYPE, Boolean.class,
                               Map.of("threshold", 0.7));
        assertThat(expr.eval(Map.of("score", 0.5))).isTrue();
        assertThat(expr.eval(Map.of("score", 0.9))).isFalse();
    }

    @Test
    void compile_undefinedVariable_returnsNull() {
        CompiledExpression<Map<String, Object>, Object> expr =
                engine.compile("missing", MAP_TYPE, Object.class);
        assertThat(expr.eval(Map.of())).isNull();
    }

    @Test
    void compile_invalidExpression_throwsCompilationException() {
        assertThatThrownBy(() -> engine.compile("!!invalid!!", MAP_TYPE, Boolean.class))
                .isInstanceOf(ExpressionCompilationException.class);
    }

    @Test
    void compile_nullTraversal_returnsFalse() {
        CompiledExpression<Map<String, Object>, Boolean> expr =
                engine.compile("item.name == 'x'", MAP_TYPE, Boolean.class);
        assertThat(Boolean.TRUE.equals(expr.eval(Map.of()))).isFalse();
    }

    @Test
    void validate_validExpression_noException() {
        engine.validate("age > 20");
    }

    @Test
    void validate_blankExpression_throwsCompilationException() {
        assertThatThrownBy(() -> engine.validate("  "))
                .isInstanceOf(ExpressionCompilationException.class);
    }

    @Test
    void validate_invalidSyntax_throwsCompilationException() {
        assertThatThrownBy(() -> engine.validate("if ("))
                .isInstanceOf(ExpressionCompilationException.class);
    }

    @Test
    void compile_cachedForSameExpression() {
        CompiledExpression<?, ?> first = engine.compile("x > 1", MAP_TYPE, Boolean.class);
        CompiledExpression<?, ?> second = engine.compile("x > 1", MAP_TYPE, Boolean.class);
        assertThat(first).isSameAs(second);
    }

    @Test
    void compile_differentExpressions_notCached() {
        CompiledExpression<?, ?> first = engine.compile("x > 1", MAP_TYPE, Boolean.class);
        CompiledExpression<?, ?> second = engine.compile("x < 1", MAP_TYPE, Boolean.class);
        assertThat(first).isNotSameAs(second);
    }
}
