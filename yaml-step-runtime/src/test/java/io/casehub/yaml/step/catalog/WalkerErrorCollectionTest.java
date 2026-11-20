package io.casehub.yaml.step.catalog;

import io.casehub.yaml.core.annotation.AnnotationMap;
import io.casehub.yaml.core.error.ParseError;
import io.casehub.yaml.core.error.YamlErrorCollector;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Portability;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.statemachine.PlaybookParser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalkerErrorCollectionTest {

    private final CompositePluginRegistry registry = new CompositePluginRegistry() {{
        register(defn("log"));
        register(defn("process"));
        register(defn("assert"));
    }};

    @Test
    void collectsUnknownStepError() {
        var steps = List.of(Map.<String, Object>of("nonexistent", "value"));
        var collector = new YamlErrorCollector();
        var resolved = Walker.resolve(steps, registry, AnnotationMap.empty(), collector, "test.yaml");

        assertThat(collector.hasErrors()).isTrue();
        assertThat(collector.errors()).hasSize(1);
        assertThat(collector.errors().get(0)).isInstanceOf(ParseError.UnknownStepError.class);
        var error = (ParseError.UnknownStepError) collector.errors().get(0);
        assertThat(error.referencedAction()).isEqualTo("nonexistent");
        assertThat(error.location().file()).isEqualTo("test.yaml");

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0)).isInstanceOf(ResolvedStep.ErrorStep.class);
        var errorStep = (ResolvedStep.ErrorStep) resolved.get(0);
        assertThat(errorStep.error()).isSameAs(collector.errors().get(0));
    }

    @Test
    void collectsMultipleErrors() {
        var steps = List.of(
                Map.<String, Object>of("unknown1", "v"),
                Map.<String, Object>of("unknown2", "v"));
        var collector = new YamlErrorCollector();
        var resolved = Walker.resolve(steps, registry, AnnotationMap.empty(), collector, "test.yaml");

        assertThat(collector.errors()).hasSize(2);
        assertThat(resolved).hasSize(2);
        assertThat(resolved.get(0)).isInstanceOf(ResolvedStep.ErrorStep.class);
        assertThat(resolved.get(1)).isInstanceOf(ResolvedStep.ErrorStep.class);
    }

    @Test
    void validStepsStillResolve() {
        var steps = List.of(Map.<String, Object>of("log", "hello"));
        var collector = new YamlErrorCollector();
        var resolved = Walker.resolve(steps, registry, AnnotationMap.empty(), collector, "test.yaml");

        assertThat(collector.hasErrors()).isFalse();
        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0)).isInstanceOf(ResolvedStep.PluginStep.class);
    }

    @Test
    void mixesValidAndErrorSteps() {
        var steps = List.of(
                Map.<String, Object>of("log", "hello"),
                Map.<String, Object>of("nonexistent", "value"),
                Map.<String, Object>of("process", Map.of("command", "run.sh")));
        var collector = new YamlErrorCollector();
        var resolved = Walker.resolve(steps, registry, AnnotationMap.empty(), collector, "test.yaml");

        assertThat(collector.errors()).hasSize(1);
        assertThat(resolved).hasSize(3);
        assertThat(resolved.get(0)).isInstanceOf(ResolvedStep.PluginStep.class);
        assertThat(resolved.get(1)).isInstanceOf(ResolvedStep.ErrorStep.class);
        assertThat(resolved.get(2)).isInstanceOf(ResolvedStep.PluginStep.class);
    }

    @Test
    void collectsDuplicateStepNameError() {
        var step1 = new LinkedHashMap<String, Object>();
        step1.put("name", "fetch");
        step1.put("log", "first");
        var step2 = new LinkedHashMap<String, Object>();
        step2.put("name", "fetch");
        step2.put("process", Map.of("command", "second"));
        var collector = new YamlErrorCollector();
        var resolved = Walker.resolve(List.of(step1, step2), registry, AnnotationMap.empty(), collector, "test.yaml");

        assertThat(collector.errors()).hasSize(1);
        assertThat(collector.errors().get(0)).isInstanceOf(ParseError.DuplicateStepError.class);
        var error = (ParseError.DuplicateStepError) collector.errors().get(0);
        assertThat(error.stepName()).isEqualTo("fetch");
    }

    @Test
    void collectsStructureErrorForEmptyStep() {
        var steps = List.<Map<String, Object>>of(Map.of());
        var collector = new YamlErrorCollector();
        var resolved = Walker.resolve(steps, registry, AnnotationMap.empty(), collector, "test.yaml");

        assertThat(collector.hasErrors()).isTrue();
        assertThat(collector.errors().get(0)).isInstanceOf(ParseError.StructureError.class);
        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0)).isInstanceOf(ResolvedStep.ErrorStep.class);
    }

    @Test
    void collectsStructureErrorForRemovedKeys() {
        var steps = List.of(Map.<String, Object>of("steps", List.of(Map.of("log", "hello"))));
        var collector = new YamlErrorCollector();
        var resolved = Walker.resolve(steps, registry, AnnotationMap.empty(), collector, "test.yaml");

        assertThat(collector.hasErrors()).isTrue();
        assertThat(collector.errors().get(0)).isInstanceOf(ParseError.StructureError.class);
        assertThat(collector.errors().get(0).summary()).contains("steps");
    }

    @Test
    void originalOverloadStillThrows() {
        var steps = List.of(Map.<String, Object>of("nonexistent", "value"));
        assertThatThrownBy(() -> Walker.resolve(steps, registry))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void errorStepPreservesOriginalYaml() {
        var originalStep = Map.<String, Object>of("nonexistent", "some-value");
        var steps = List.of(originalStep);
        var collector = new YamlErrorCollector();
        var resolved = Walker.resolve(steps, registry, AnnotationMap.empty(), collector, "test.yaml");

        var errorStep = (ResolvedStep.ErrorStep) resolved.get(0);
        assertThat(errorStep.originalYaml()).containsEntry("nonexistent", "some-value");
    }

    @Test
    void errorStepHasEmptyDecorators() {
        var steps = List.of(Map.<String, Object>of("nonexistent", "value"));
        var collector = new YamlErrorCollector();
        var resolved = Walker.resolve(steps, registry, AnnotationMap.empty(), collector, "test.yaml");

        var errorStep = (ResolvedStep.ErrorStep) resolved.get(0);
        assertThat(errorStep.decorators()).isEmpty();
    }

    @Test
    void collectsErrorInNestedBlock() {
        var step = new LinkedHashMap<String, Object>();
        step.put("block", List.of(
                Map.<String, Object>of("log", "valid"),
                Map.<String, Object>of("nonexistent", "bad")));
        var collector = new YamlErrorCollector();
        var resolved = Walker.resolve(List.of(step), registry, AnnotationMap.empty(), collector, "test.yaml");

        assertThat(collector.errors()).hasSize(1);
        assertThat(collector.errors().get(0)).isInstanceOf(ParseError.UnknownStepError.class);
        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0)).isInstanceOf(ResolvedStep.BlockStep.class);
        var block = (ResolvedStep.BlockStep) resolved.get(0);
        assertThat(block.steps()).hasSize(2);
        assertThat(block.steps().get(0)).isInstanceOf(ResolvedStep.PluginStep.class);
        assertThat(block.steps().get(1)).isInstanceOf(ResolvedStep.ErrorStep.class);
    }


    @SuppressWarnings("unchecked")
    @Test
    void errorLocationCarriesLineAndColumn_whenParsedWithLocations() {
        String yaml = """
                      name: test
                      ---
                      - log: hello
                      - nonexistent: bad
                      - process:
                          command: run.sh
                      """;
        var parsed    = PlaybookParser.parseWithLocations(yaml, "playbook.yaml");
        var steps     = (List<Map<String, Object>>) parsed.document().content().get("steps");
        var collector = new YamlErrorCollector();
        Walker.resolve(steps, registry, AnnotationMap.empty(), collector, "playbook.yaml", parsed.locations());

        assertThat(collector.errors()).hasSize(1);
        var error = collector.errors().get(0);
        assertThat(error.location().file()).isEqualTo("playbook.yaml");
        assertThat(error.location().line()).isEqualTo(4);
        assertThat(error.location().isKnown()).isTrue();
    }

    private static Definition defn(String name) {
        return new Definition(name, null, Map.of(), Map.of(), Portability.JAVA,
                (params, services) -> Result.of(Map.of()), null);
    }
}
