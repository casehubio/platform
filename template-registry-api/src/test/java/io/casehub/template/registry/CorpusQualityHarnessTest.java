package io.casehub.template.registry;

import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CorpusQualityHarnessTest {

    private InMemoryTemplateRegistry registry;
    private CorpusQualityHarness harness;

    @BeforeEach
    void setUp() {
        registry = new InMemoryTemplateRegistry();
        harness = new CorpusQualityHarness(registry);
    }

    @Test
    void evaluate_allFixturesMatch() {
        registry.register(new TemplateNamespace("ns", Map.of("greet",
            TemplateDefinition.builder("greet")
                .pattern("hello ${target}")
                .variable("target", VariableConstraint.string())
                .build())));

        var fixtures = List.of(
            new VariationFixture("greet", "hello world", true, Map.of("target", "world")),
            new VariationFixture("greet", "hello team", true, Map.of("target", "team"))
        );

        var report = harness.evaluate("ns", fixtures);

        assertThat(report.namespace()).isEqualTo("ns");
        assertThat(report.totalVariations()).isEqualTo(2);
        assertThat(report.totalMatched()).isEqualTo(2);
        assertThat(report.matchRate()).isEqualTo(1.0);
        assertThat(report.totalFalsePositives()).isZero();
        assertThat(report.templates()).hasSize(1);
        assertThat(report.templates().get(0).templateName()).isEqualTo("greet");
    }

    @Test
    void evaluate_missedVariation() {
        registry.register(new TemplateNamespace("ns", Map.of("greet",
            TemplateDefinition.builder("greet")
                .pattern("hello ${target}")
                .variable("target", VariableConstraint.string())
                .build())));

        var fixtures = List.of(
            new VariationFixture("greet", "hello world", true, Map.of("target", "world")),
            new VariationFixture("greet", "good morning everyone", true, Map.of())
        );

        var report = harness.evaluate("ns", fixtures);

        assertThat(report.totalVariations()).isEqualTo(2);
        assertThat(report.totalMatched()).isEqualTo(1);
        assertThat(report.matchRate()).isEqualTo(0.5);
    }

    @Test
    void evaluate_negativeFixturePassesWhenUnmatched() {
        registry.register(new TemplateNamespace("ns", Map.of("greet",
            TemplateDefinition.builder("greet")
                .pattern("hello ${target}")
                .variable("target", VariableConstraint.string())
                .build())));

        var fixtures = List.of(
            new VariationFixture("greet", "completely unrelated text", false, Map.of())
        );

        var report = harness.evaluate("ns", fixtures);

        assertThat(report.templates().get(0).variations().get(0).passed()).isTrue();
    }

    @Test
    void evaluate_negativeFixtureFailsWhenMatched() {
        registry.register(new TemplateNamespace("ns", Map.of("greet",
            TemplateDefinition.builder("greet")
                .pattern("hello ${target}")
                .variable("target", VariableConstraint.string())
                .build())));

        var fixtures = List.of(
            new VariationFixture("greet", "hello world", false, Map.of())
        );

        var report = harness.evaluate("ns", fixtures);

        var variation = report.templates().get(0).variations().get(0);
        assertThat(variation.passed()).isFalse();
        assertThat(report.totalFalsePositives()).isEqualTo(1);
    }

    @Test
    void evaluate_fieldMismatchCountsAsFailure() {
        registry.register(new TemplateNamespace("ns", Map.of("greet",
            TemplateDefinition.builder("greet")
                .pattern("hello ${target}")
                .variable("target", VariableConstraint.string())
                .build())));

        var fixtures = List.of(
            new VariationFixture("greet", "hello world", true, Map.of("target", "wrong_value"))
        );

        var report = harness.evaluate("ns", fixtures);

        assertThat(report.templates().get(0).fieldMismatchCount()).isEqualTo(1);
        assertThat(report.templates().get(0).variations().get(0).passed()).isFalse();
    }

    @Test
    void evaluate_emptyFixturesReturnsEmptyReport() {
        registry.register(new TemplateNamespace("ns", Map.of("greet",
            TemplateDefinition.builder("greet")
                .pattern("hello ${target}")
                .variable("target", VariableConstraint.string())
                .build())));

        var report = harness.evaluate("ns", List.of());

        assertThat(report.totalVariations()).isZero();
        assertThat(report.matchRate()).isEqualTo(1.0);
        assertThat(report.templates()).isEmpty();
    }

    @Test
    void evaluate_crossNamespaceCollisionDetected() {
        registry.register(new TemplateNamespace("ns1", Map.of("greet",
            TemplateDefinition.builder("greet")
                .pattern("hello ${target}")
                .variable("target", VariableConstraint.string())
                .build())));
        registry.register(new TemplateNamespace("ns2", Map.of("salute",
            TemplateDefinition.builder("salute")
                .pattern("hello ${name}")
                .variable("name", VariableConstraint.string())
                .build())));

        var fixtures = List.of(
            new VariationFixture("greet", "hello world", true, Map.of("target", "world"))
        );

        var report = harness.evaluate("ns1", fixtures);

        assertThat(report.collisions()).isNotEmpty();
        assertThat(report.collisions().get(0).collidingNamespace()).isEqualTo("ns2");
    }
}
