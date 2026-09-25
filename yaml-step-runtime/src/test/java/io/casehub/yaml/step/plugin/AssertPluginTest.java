package io.casehub.yaml.step.plugin;

import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionContext;
import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.StepAction;
import io.casehub.yaml.plugin.api.StepResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssertPluginTest {

    @Test
    void passesWhenExpressionIsTrue() {
        ExpressionEngineRegistry engines = mockEngineReturning(true);
        var registry = new MapServiceRegistry()
                .register(ExpressionEngineRegistry.class, engines);

        var action = loadAction();
        StepResult result = action.execute(
                Map.of("expression", "1 == 1"), registry);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("passed", true);
    }

    @Test
    void failsWhenExpressionIsFalse() {
        ExpressionEngineRegistry engines = mockEngineReturning(false);
        var registry = new MapServiceRegistry()
                .register(ExpressionEngineRegistry.class, engines);

        var action = loadAction();
        StepResult result = action.execute(
                Map.of("expression", "1 == 2"), registry);

        assertThat(result.isSuccess()).isFalse();
        assertThat(((StepResult.Failure) result).message())
                .contains("1 == 2");
    }

    @Test
    void usesCustomMessageOnFailure() {
        ExpressionEngineRegistry engines = mockEngineReturning(false);
        var registry = new MapServiceRegistry()
                .register(ExpressionEngineRegistry.class, engines);

        var action = loadAction();
        StepResult result = action.execute(
                Map.of("expression", "false", "message", "Deploy check failed"), registry);

        assertThat(result.isSuccess()).isFalse();
        assertThat(((StepResult.Failure) result).message())
                .isEqualTo("Deploy check failed");
    }

    @Test
    void failsWhenNoDefaultEngine() {
        ExpressionEngineRegistry engines = mock(ExpressionEngineRegistry.class);
        when(engines.resolveDefault(ExpressionContext.CONDITION)).thenReturn(null);
        var registry = new MapServiceRegistry()
                .register(ExpressionEngineRegistry.class, engines);

        var action = loadAction();
        StepResult result = action.execute(
                Map.of("expression", "true"), registry);

        assertThat(result.isSuccess()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private ExpressionEngineRegistry mockEngineReturning(boolean value) {
        ExpressionEngineRegistry engines = mock(ExpressionEngineRegistry.class);
        when(engines.resolveDefault(ExpressionContext.CONDITION)).thenReturn("mvel");
        CompiledExpression<Map, Boolean> compiled = mock(CompiledExpression.class);
        when(compiled.eval(any())).thenReturn(value);
        when(engines.compile(eq("mvel"), any(), eq(Map.class), eq(Boolean.class)))
                .thenReturn(compiled);
        return engines;
    }

    private StepAction loadAction() {
        try {
            Class<?> clazz = Class.forName(
                    "io.casehub.yaml.step.plugin.AssertPluginAction");
            return (StepAction) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new AssertionError("APT-generated AssertPluginAction not found: " + e, e);
        }
    }
}
