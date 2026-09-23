package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComputeBlockTest {

    @Test
    void constructor_withEngineAndExpression() {
        var block = new ComputeBlock("jq", ".prices | map(select(.change > 0.05))");
        assertThat(block.engine()).isEqualTo("jq");
        assertThat(block.expression()).isEqualTo(".prices | map(select(.change > 0.05))");
    }

    @Test
    void constructor_nullEngine_throws() {
        assertThatThrownBy(() -> new ComputeBlock(null, "expr"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("engine");
    }

    @Test
    void constructor_nullExpression_throws() {
        assertThatThrownBy(() -> new ComputeBlock("jq", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("expression");
    }

    @Test
    void parse_mapWithEngineAndExpression() {
        var block = ComputeBlock.parse(Map.of("engine", "jq", "expression", ".x"), null);
        assertThat(block.engine()).isEqualTo("jq");
        assertThat(block.expression()).isEqualTo(".x");
    }

    @Test
    void parse_mapWithExpressionOnly_usesDefaultEngine() {
        var block = ComputeBlock.parse(Map.of("expression", ".x"), "mvel");
        assertThat(block.engine()).isEqualTo("mvel");
        assertThat(block.expression()).isEqualTo(".x");
    }

    @Test
    void parse_mapWithExpressionOnly_nullDefaultEngine_throws() {
        assertThatThrownBy(() -> ComputeBlock.parse(Map.of("expression", ".x"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("engine");
    }

    @Test
    void parse_mapExplicitEngineOverridesDefault() {
        var block = ComputeBlock.parse(Map.of("engine", "jq", "expression", ".x"), "mvel");
        assertThat(block.engine()).isEqualTo("jq");
    }

    @Test
    void parse_mapMissingExpression_throws() {
        assertThatThrownBy(() -> ComputeBlock.parse(Map.of("engine", "jq"), null))
                .isInstanceOf(NullPointerException.class);
    }
}
