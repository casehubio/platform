package io.casehub.platform.expression;

import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionCompilationException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MvelExpressionEngineTest {

    private final MvelExpressionEngine engine = new MvelExpressionEngine();

    @SuppressWarnings("unchecked")
    private static final Class<Map<String, Object>> MAP_TYPE =
            (Class<Map<String, Object>>) (Class<?>) Map.class;

    @Test
    void type_returnsMvel() {
        assertThat(engine.type()).isEqualTo("mvel");
    }

    @Test
    void compile_mapContext_arithmeticExpression() {
        CompiledExpression<Map<String, Object>, Integer> expr =
                engine.compile("x + y", MAP_TYPE, Integer.class);
        assertThat(expr.eval(Map.of("x", 3, "y", 5))).isEqualTo(8);
    }

    @Test
    void compile_mapContext_booleanExpression() {
        CompiledExpression<Map<String, Object>, Boolean> expr =
                engine.compile("age > 20", MAP_TYPE, Boolean.class);
        assertThat(expr.eval(Map.of("age", 25))).isTrue();
    }

    @Test
    void compile_mapContext_stringEquality() {
        CompiledExpression<Map<String, Object>, Boolean> expr =
                engine.compile("name == \"Alice\"", MAP_TYPE, Boolean.class);
        assertThat(expr.eval(Map.of("name", "Alice"))).isTrue();
        assertThat(expr.eval(Map.of("name", "Bob"))).isFalse();
    }

    @Test
    void compile_withVariables_parameterizedExpression() {
        CompiledExpression<Map<String, Object>, Boolean> expr =
                engine.compile("name == $p0", MAP_TYPE, Boolean.class,
                               Map.of("$p0", "Alice"));
        assertThat(expr.eval(Map.of("name", "Alice"))).isTrue();
        assertThat(expr.eval(Map.of("name", "Bob"))).isFalse();
    }

    @Test
    void compile_booleanLogic() {
        CompiledExpression<Map<String, Object>, Boolean> expr =
                engine.compile("age > 18 && name != \"Bob\"", MAP_TYPE, Boolean.class);
        assertThat(expr.eval(Map.of("age", 25, "name", "Alice"))).isTrue();
        assertThat(expr.eval(Map.of("age", 25, "name", "Bob"))).isFalse();
        assertThat(expr.eval(Map.of("age", 16, "name", "Alice"))).isFalse();
    }

    @Test
    void compile_invalidExpression_throwsCompilationException() {
        CompiledExpression<Map<String, Object>, Boolean> expr =
                engine.compile("!!invalid!!", MAP_TYPE, Boolean.class);
        assertThatThrownBy(() -> expr.eval(Map.of("x", 1)))
                .isInstanceOf(ExpressionCompilationException.class);
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
    void compile_cachedForSameExpression() {
        CompiledExpression<?, ?> first  = engine.compile("x + y", MAP_TYPE, Integer.class);
        CompiledExpression<?, ?> second = engine.compile("x + y", MAP_TYPE, Integer.class);
        assertThat(first).isSameAs(second);
    }

    @Test
    void compile_differentExpressions_notCached() {
        CompiledExpression<?, ?> first  = engine.compile("x + y", MAP_TYPE, Integer.class);
        CompiledExpression<?, ?> second = engine.compile("x - y", MAP_TYPE, Integer.class);
        assertThat(first).isNotSameAs(second);
    }
}
