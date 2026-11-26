package io.casehub.template.registry;

import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;


import static org.assertj.core.api.Assertions.assertThat;

class CorpusQualityGateTest {

    private static final double MIN_MATCH_RATE = 0.8;

    private static InMemoryTemplateRegistry registry;
    private static CorpusQualityHarness harness;

    @BeforeAll
    static void setUpRegistry() {
        registry = new InMemoryTemplateRegistry();
        registry.register(new TemplateNamespace("test:greetings", Map.of(
            "hello", TemplateDefinition.builder("hello")
                .pattern("hello ${target}")
                .variable("target", VariableConstraint.string())
                .build(),
            "goodbye", TemplateDefinition.builder("goodbye")
                .pattern("goodbye ${target}")
                .variable("target", VariableConstraint.string())
                .build()
        )));
        harness = new CorpusQualityHarness(registry);
    }

    static List<Path> loadFixtureFiles() throws URISyntaxException, IOException {
        Path fixturesDir = Paths.get(
            CorpusQualityGateTest.class.getResource("/fixtures").toURI());
        try (var stream = Files.list(fixturesDir)) {
            return stream
                .filter(p -> p.toString().endsWith(".variations.yaml"))
                .sorted()
                .toList();
        }
    }

    @ParameterizedTest
    @MethodSource("loadFixtureFiles")
    void corpusQualityGate(Path fixtureFile) {
        String namespaceName = VariationFixtureLoader.namespace(fixtureFile);
        List<VariationFixture> fixtures = VariationFixtureLoader.load(fixtureFile);
        CorpusQualityReport report = harness.evaluate(namespaceName, fixtures);

        for (TemplateReport template : report.templates()) {
            assertThat(template.matchRate())
                .as("template '%s' match rate", template.templateName())
                .isGreaterThanOrEqualTo(MIN_MATCH_RATE);
            assertThat(template.fieldMismatchCount())
                .as("template '%s' field mismatches", template.templateName())
                .isZero();
            assertThat(template.falsePositiveCount())
                .as("template '%s' false positives", template.templateName())
                .isZero();
        }

        assertThat(report.collisions())
            .as("cross-namespace collisions")
            .isEmpty();
    }
}
