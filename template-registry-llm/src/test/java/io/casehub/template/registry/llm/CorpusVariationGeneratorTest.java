package io.casehub.template.registry.llm;

import io.casehub.template.registry.InMemoryTemplateRegistry;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.VariationFixtureLoader;
import io.casehub.template.registry.VariationSeeder;
import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CorpusVariationGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void run_generatesVariationsAndWritesFixtures() {
        VariationSeeder stubSeeder = (template, language) ->
            List.of("hi ${target}", "hey ${target}");

        var namespace = new TemplateNamespace("test:greetings", Map.of("greet",
            TemplateDefinition.builder("greet")
                .pattern("hello ${target}")
                .variable("target", VariableConstraint.string())
                .build()));

        var registry = new InMemoryTemplateRegistry();
        registry.register(namespace);

        Path output = tempDir.resolve("output.variations.yaml");

        var report = CorpusVariationGenerator.run(
            namespace, stubSeeder, registry, output, 0.0);

        assertThat(report.totalVariations()).isGreaterThan(0);
        assertThat(output).exists();

        var loaded = VariationFixtureLoader.load(output);
        assertThat(loaded).isNotEmpty();
        assertThat(VariationFixtureLoader.namespace(output)).isEqualTo("test:greetings");
    }

    @Test
    void run_reportContainsAllTemplates() {
        VariationSeeder stubSeeder = (template, language) ->
            List.of("variation of " + template.name());

        var namespace = new TemplateNamespace("ns", Map.of(
            "t1", TemplateDefinition.builder("t1").pattern("pattern one").build(),
            "t2", TemplateDefinition.builder("t2").pattern("pattern two").build()
        ));

        var registry = new InMemoryTemplateRegistry();
        registry.register(namespace);

        Path output = tempDir.resolve("out.yaml");
        var report = CorpusVariationGenerator.run(
            namespace, stubSeeder, registry, output, 0.0);

        assertThat(report.templates()).hasSize(2);
    }
}
