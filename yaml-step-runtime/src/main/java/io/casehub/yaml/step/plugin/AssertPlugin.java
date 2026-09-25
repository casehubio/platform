package io.casehub.yaml.step.plugin;

import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionContext;
import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Optional;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.StepPlugin;
import io.casehub.yaml.plugin.api.StepResult;

import java.util.Map;

@StepPlugin(value = "assert", description = "Asserts an expression evaluates to true")
public record AssertPlugin(
        @Required String expression,
        @Optional String message) {

    @Execute
    public StepResult run(ExpressionEngineRegistry engines) {
        String engineType = engines.resolveDefault(ExpressionContext.CONDITION);
        if (engineType == null) {
            return StepResult.failed(
                    "No default expression engine configured for CONDITION context");
        }

        try {
            @SuppressWarnings("unchecked")
            CompiledExpression<Map, Boolean> compiled =
                    engines.compile(engineType, expression, Map.class, Boolean.class);
            Boolean result = compiled.eval(Map.of());

            if (Boolean.TRUE.equals(result)) {
                return StepResult.of(Map.of("passed", true));
            }

            String failMessage = message != null
                    ? message
                    : "Assertion failed: " + expression;
            return StepResult.failed(failMessage);
        } catch (Exception e) {
            return StepResult.failed("Expression evaluation failed: " + e.getMessage());
        }
    }
}
