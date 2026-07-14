package io.casehub.platform.expression;

import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpExpressionEngineRegistryTest {

    private final ExpressionEngineRegistry registry = new NoOpExpressionEngineRegistry();

    @Test
    void resolve_returnsEmpty() {
        assertThat(registry.resolve("mvel")).isEmpty();
        assertThat(registry.resolve("jq")).isEmpty();
    }

    @Test
    void compile_throwsUnsupported() {
        assertThatThrownBy(() -> registry.compile("mvel", "x > 1", Object.class, Boolean.class))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("casehub-platform-expression");
    }

    @Test
    void compileWithVariables_throwsUnsupported() {
        assertThatThrownBy(() -> registry.compile("mvel", "x > 1",
                Object.class, Boolean.class, Map.of()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("casehub-platform-expression");
    }

    @Test
    void validate_throwsUnsupported() {
        assertThatThrownBy(() -> registry.validate("mvel", "x > 1"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("casehub-platform-expression");
    }

    @Test
    void register_silentNoOp() {
        registry.register(null);
        assertThat(registry.resolve("anything")).isEmpty();
    }
}
