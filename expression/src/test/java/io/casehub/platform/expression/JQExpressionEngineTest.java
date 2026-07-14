package io.casehub.platform.expression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionCompilationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JQExpressionEngineTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final JQExpressionEngine engine = new JQExpressionEngine();

    @Test
    void type_returnsJq() {
        assertThat(engine.type()).isEqualTo("jq");
    }

    @SuppressWarnings("unchecked")
    @Test
    void compile_fieldExtraction_listResult() {
        ObjectNode input = MAPPER.createObjectNode().put("status", "active");
        CompiledExpression<JsonNode, List<JsonNode>> expr =
                engine.compile(".status", JsonNode.class, (Class<List<JsonNode>>) (Class<?>) List.class);
        List<JsonNode> result = expr.eval(input);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).asText()).isEqualTo("active");
    }

    @Test
    void compile_booleanExpression() {
        ObjectNode input = MAPPER.createObjectNode().put("age", 25);
        CompiledExpression<JsonNode, Boolean> expr =
                engine.compile(".age > 20", JsonNode.class, Boolean.class);
        assertThat(expr.eval(input)).isTrue();
    }

    @Test
    void compile_booleanExpression_false() {
        ObjectNode input = MAPPER.createObjectNode().put("age", 15);
        CompiledExpression<JsonNode, Boolean> expr =
                engine.compile(".age > 20", JsonNode.class, Boolean.class);
        assertThat(expr.eval(input)).isFalse();
    }

    @Test
    void compile_invalidExpression_throwsCompilationException() {
        assertThatThrownBy(() -> engine.compile("invalid jq [[[", JsonNode.class, Boolean.class))
                .isInstanceOf(ExpressionCompilationException.class);
    }

    @Test
    void validate_validExpression_noException() {
        engine.validate(".status");
    }

    @Test
    void validate_invalidExpression_throwsCompilationException() {
        assertThatThrownBy(() -> engine.validate("invalid jq [[["))
                .isInstanceOf(ExpressionCompilationException.class);
    }

    @Test
    void compile_cachedForSameExpression() {
        CompiledExpression<?, ?> first = engine.compile(".status", JsonNode.class, Boolean.class);
        CompiledExpression<?, ?> second = engine.compile(".status", JsonNode.class, Boolean.class);
        assertThat(first).isSameAs(second);
    }
}
