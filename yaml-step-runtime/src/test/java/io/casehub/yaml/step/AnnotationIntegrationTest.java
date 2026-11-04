package io.casehub.yaml.step;

import io.casehub.yaml.core.annotation.AnnotationExtractor;
import io.casehub.yaml.core.annotation.InjectionBinding;
import io.casehub.yaml.core.annotation.InjectionResolver;
import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Portability;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import io.casehub.yaml.step.catalog.ResolvedStep;
import io.casehub.yaml.step.catalog.Walker;
import io.casehub.yaml.step.eval.StepRunner;
import io.casehub.yaml.step.eval.StructuralStepEvaluator;
import io.casehub.yaml.step.statemachine.PlaybookParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationIntegrationTest {

    private static Definition defn(String name) {
        return new Definition(name, null, Map.of(), Map.of(), Portability.JAVA,
                (params, services) -> Result.of(Map.of()), null);
    }

    private final CompositePluginRegistry registry = new CompositePluginRegistry() {{
        register(defn("rest-request"));
        register(defn("agent-invoke"));
    }};

    @Test
    @SuppressWarnings("unchecked")
    void fullPipelineWithAnnotationsAndInject() {
        String yaml = """
                #@description("Integration test playbook")
                #@requires(agent)
                #@Inject(AgentProvider, qualifier: "claude")
                claude: agent
                name: Integration Test
                steps:
                  #@timeout(2s)
                  - rest-request:
                      url: /api/test
                  #@retry(3)
                  - agent-invoke:
                      prompt: hello
                """;

        var annotationMap = AnnotationExtractor.extract(yaml);
        assertTrue(annotationMap.forDocument("description").isPresent());
        assertEquals("Integration test playbook",
            annotationMap.forDocument("description").get().stringArg(0));
        assertTrue(annotationMap.forDocument("requires").isPresent());

        var doc = PlaybookParser.parse(yaml);
        assertEquals("Integration Test", doc.content().get("name"));
        assertEquals("agent", doc.content().get("claude"));

        var bindings = InjectionBinding.extractAll(annotationMap, doc.content());
        assertEquals(1, bindings.size());
        assertEquals("claude", bindings.get(0).variableName());
        assertEquals("AgentProvider", bindings.get(0).typeName());
        assertEquals("claude", bindings.get(0).qualifiers().get("qualifier"));

        var steps = (List<Map<String, Object>>) doc.content().get("steps");
        var resolved = Walker.resolve(steps, registry, annotationMap);

        assertEquals(2, resolved.size());
        assertEquals("2s", resolved.get(0).decorators().get("timeout"));
        assertEquals(3, resolved.get(1).decorators().get("retry"));
        assertTrue(resolved.get(0).annotations().stream()
            .anyMatch(a -> a.name().equals("timeout")));

        InjectionResolver injResolver = (typeName, qualifiers) ->
            Map.of("name", "test-" + typeName, "model", "opus");
        var evaluator = new StructuralStepEvaluator(new ConditionEvaluator(null));
        var enriched = evaluator.withInjections(bindings, injResolver);

        var executedSteps = new ArrayList<String>();
        StepRunner runner = (step, vr) -> {
            if (step instanceof ResolvedStep.PluginStep ps) {
                executedSteps.add(ps.actionName());
            }
            return Result.of(Map.of("status", "ok"));
        };

        var resolver = new VariableResolver(Map.of(), Set.of());
        for (var step : resolved) {
            enriched.evaluate(step, resolver, runner);
        }

        assertEquals(List.of("rest-request", "agent-invoke"), executedSteps);
    }

    @Test
    void annotationsWithNoStepsExtractCorrectly() {
        String yaml = """
                #@description("Simple playbook")
                #@version(1.0)
                name: Simple
                """;

        var annotationMap = AnnotationExtractor.extract(yaml);
        assertEquals(2, annotationMap.documentAnnotations().size());
        assertTrue(annotationMap.forDocument("description").isPresent());
        assertTrue(annotationMap.forDocument("version").isPresent());
        assertEquals("1.0", annotationMap.forDocument("version").get().stringArg(0));
    }

    @Test
    void dualPathKeyAndAnnotationCoexist() {
        String yaml = """
                name: Dual Path Test
                steps:
                  #@timeout(5s)
                  - rest-request:
                      url: /api
                """;

        var annotationMap = AnnotationExtractor.extract(yaml);
        var doc = PlaybookParser.parse(yaml);

        @SuppressWarnings("unchecked")
        var steps = (List<Map<String, Object>>) doc.content().get("steps");
        var resolved = Walker.resolve(steps, registry, annotationMap);

        assertEquals("5s", resolved.get(0).decorators().get("timeout"));
    }
}
