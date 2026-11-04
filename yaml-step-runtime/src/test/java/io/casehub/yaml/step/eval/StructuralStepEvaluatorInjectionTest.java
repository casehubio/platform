package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.annotation.InjectionBinding;
import io.casehub.yaml.core.annotation.InjectionResolver;
import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.ResolvedStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class StructuralStepEvaluatorInjectionTest {

    @Test
    @SuppressWarnings("unchecked")
    void injectedBindingsAvailableInResolver() {
        var evaluator = new StructuralStepEvaluator(new ConditionEvaluator(null));

        var binding = new InjectionBinding("claude", "agent", "AgentProvider", Map.of("qualifier", "claude"));
        InjectionResolver resolver = (typeName, qualifiers) -> Map.of("name", "claude-agent", "ready", true);

        var enrichedEvaluator = evaluator.withInjections(List.of(binding), resolver);

        var capturedResolver = new AtomicReference<VariableResolver>();
        StepRunner runner = (step, vr) -> {
            capturedResolver.set(vr);
            return Result.of(Map.of());
        };

        var step = new ResolvedStep.PluginStep("test", "test-action", Map.of(), Map.of());
        var baseResolver = new VariableResolver(Map.of(), Set.of());

        enrichedEvaluator.evaluate(step, baseResolver, runner);

        assertNotNull(capturedResolver.get());
        Object resolved = capturedResolver.get().resolve("${claude.name}");
        assertEquals("claude-agent", resolved);
    }

    @Test
    void unresolvedBindingThrows() {
        var evaluator = new StructuralStepEvaluator(new ConditionEvaluator(null));

        var binding = new InjectionBinding("claude", "agent", "AgentProvider", Map.of());
        InjectionResolver resolver = (typeName, qualifiers) -> null;

        assertThrows(IllegalStateException.class, () ->
            evaluator.withInjections(List.of(binding), resolver));
    }

    @Test
    void noInjectionsDoesNotAffectResolver() {
        var evaluator = new StructuralStepEvaluator(new ConditionEvaluator(null));

        var capturedResolver = new AtomicReference<VariableResolver>();
        StepRunner runner = (step, vr) -> {
            capturedResolver.set(vr);
            return Result.of(Map.of());
        };

        var step = new ResolvedStep.PluginStep("test", "test-action", Map.of(), Map.of());
        var baseResolver = new VariableResolver(Map.of(), Set.of());

        evaluator.evaluate(step, baseResolver, runner);
        assertNotNull(capturedResolver.get());
    }
}
