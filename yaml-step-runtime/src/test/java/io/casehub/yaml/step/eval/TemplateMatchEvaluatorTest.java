package io.casehub.yaml.step.eval;

import io.casehub.template.registry.InMemoryTemplateRegistry;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.resolver.VariableConstraint;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.ResolvedStep;
import io.casehub.yaml.step.catalog.TemplateMatchSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateMatchEvaluatorTest {

    private InMemoryTemplateRegistry registry;
    private StructuralStepEvaluator evaluator;

    @BeforeEach
    void setUp() {
        registry = new InMemoryTemplateRegistry();
        registry.register(new TemplateNamespace("protocol:review",
            Map.of(
                "vote", TemplateDefinition.builder("vote")
                    .pattern("I ${decision} this ${subject}")
                    .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                    .variable("subject", VariableConstraint.string())
                    .build(),
                "escalate", TemplateDefinition.builder("escalate")
                    .pattern("escalate to ${target} because ${reason}")
                    .variable("target", VariableConstraint.string())
                    .variable("reason", VariableConstraint.string())
                    .build()
            )));
        evaluator = new StructuralStepEvaluator(
            new ConditionEvaluator(null), null, registry);
    }

    private ResolvedStep.PluginStep leaf(String name) {
        return new ResolvedStep.PluginStep(name, "assert",
            Map.of("that", "true", "equals", "true"), Map.of());
    }

    @Test
    void templateMatch_dispatchesToVoteHandler() {
        var voteHandler = List.<ResolvedStep>of(leaf("vote-handler"));
        var escalateHandler = List.<ResolvedStep>of(leaf("escalate-handler"));
        var handlers = new LinkedHashMap<String, List<ResolvedStep>>();
        handlers.put("vote", voteHandler);
        handlers.put("escalate", escalateHandler);

        var spec = new TemplateMatchSpec("protocol:review", "${params.input}", handlers, List.of());
        var step = ResolvedStep.MatchStep.templateMode("m1", spec, Map.<String, Object>of());

        var resolver = VariableResolver.forParams(Map.of(),
            Map.of("input", "I approve this architecture"), Set.of());

        var order = new ArrayList<String>();
        StepRunner runner = (s, r) -> {
            if (s instanceof ResolvedStep.PluginStep ps) order.add(ps.name());
            return Result.of(Map.of());
        };

        var result = evaluator.evaluate(step, resolver, runner);
        assertThat(result.isSuccess()).isTrue();
        assertThat(order).containsExactly("vote-handler");
    }

    @Test
    void templateMatch_dispatchesToEscalateHandler() {
        var voteHandler = List.<ResolvedStep>of(leaf("vote-handler"));
        var escalateHandler = List.<ResolvedStep>of(leaf("escalate-handler"));
        var handlers = new LinkedHashMap<String, List<ResolvedStep>>();
        handlers.put("vote", voteHandler);
        handlers.put("escalate", escalateHandler);

        var spec = new TemplateMatchSpec("protocol:review", "${params.input}", handlers, List.of());
        var step = ResolvedStep.MatchStep.templateMode("m1", spec, Map.<String, Object>of());

        var resolver = VariableResolver.forParams(Map.of(),
            Map.of("input", "escalate to manager because critical"), Set.of());

        var order = new ArrayList<String>();
        StepRunner runner = (s, r) -> {
            if (s instanceof ResolvedStep.PluginStep ps) order.add(ps.name());
            return Result.of(Map.of());
        };

        var result = evaluator.evaluate(step, resolver, runner);
        assertThat(result.isSuccess()).isTrue();
        assertThat(order).containsExactly("escalate-handler");
    }

    @Test
    void templateMatch_unmatchedHandler_executesOnNoMatch() {
        var unmatchedHandler = List.<ResolvedStep>of(leaf("unmatched-handler"));
        var handlers = new LinkedHashMap<String, List<ResolvedStep>>();
        handlers.put("vote", List.of(leaf("vote-handler")));

        var spec = new TemplateMatchSpec("protocol:review", "${params.input}", handlers, unmatchedHandler);
        var step = ResolvedStep.MatchStep.templateMode("m1", spec, Map.<String, Object>of());

        var resolver = VariableResolver.forParams(Map.of(),
            Map.of("input", "hello world"), Set.of());

        var order = new ArrayList<String>();
        StepRunner runner = (s, r) -> {
            if (s instanceof ResolvedStep.PluginStep ps) order.add(ps.name());
            return Result.of(Map.of());
        };

        var result = evaluator.evaluate(step, resolver, runner);
        assertThat(result.isSuccess()).isTrue();
        assertThat(order).containsExactly("unmatched-handler");
    }

    @Test
    void templateMatch_noHandlerForMatchedTemplate_fallsToUnmatched() {
        var unmatchedHandler = List.<ResolvedStep>of(leaf("unmatched-handler"));
        var handlers = new LinkedHashMap<String, List<ResolvedStep>>();
        handlers.put("escalate", List.of(leaf("escalate-handler")));

        var spec = new TemplateMatchSpec("protocol:review", "${params.input}", handlers, unmatchedHandler);
        var step = ResolvedStep.MatchStep.templateMode("m1", spec, Map.<String, Object>of());

        var resolver = VariableResolver.forParams(Map.of(),
            Map.of("input", "I approve this design"), Set.of());

        var order = new ArrayList<String>();
        StepRunner runner = (s, r) -> {
            if (s instanceof ResolvedStep.PluginStep ps) order.add(ps.name());
            return Result.of(Map.of());
        };

        var result = evaluator.evaluate(step, resolver, runner);
        assertThat(result.isSuccess()).isTrue();
        assertThat(order).containsExactly("unmatched-handler");
    }

    @Test
    void templateMatch_noMatch_noUnmatched_returnsEmptySuccess() {
        var handlers = new LinkedHashMap<String, List<ResolvedStep>>();
        handlers.put("vote", List.of(leaf("vote-handler")));

        var spec = new TemplateMatchSpec("protocol:review", "${params.input}", handlers, List.of());
        var step = ResolvedStep.MatchStep.templateMode("m1", spec, Map.<String, Object>of());

        var resolver = VariableResolver.forParams(Map.of(),
            Map.of("input", "hello world"), Set.of());

        var result = evaluator.evaluate(step, resolver, (s, r) -> Result.of(Map.of()));
        assertThat(result.isSuccess()).isTrue();
    }
}
