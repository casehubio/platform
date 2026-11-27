package io.casehub.template.registry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VariationFixtureWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void write_roundTripsWithLoader() {
        Path file = tempDir.resolve("output.variations.yaml");
        var fixtures = List.of(
            new VariationFixture("vote", "I approve this design", true,
                Map.of("decision", "approve", "subject", "design")),
            new VariationFixture("vote", "random noise", false, Map.of())
        );

        VariationFixtureWriter.write(file, "protocol:review", fixtures);

        assertThat(VariationFixtureLoader.namespace(file)).isEqualTo("protocol:review");
        List<VariationFixture> loaded = VariationFixtureLoader.load(file);
        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(0).templateName()).isEqualTo("vote");
        assertThat(loaded.get(0).input()).isEqualTo("I approve this design");
        assertThat(loaded.get(0).expectMatch()).isTrue();
        assertThat(loaded.get(0).expectedFields()).containsEntry("decision", "approve");
        assertThat(loaded.get(1).expectMatch()).isFalse();
    }

    @Test
    void fromReport_extractsPositiveFixtures() {
        var matched = new TemplateMatch.Matched("ns", "greet", 0,
            Map.of("target", "world"), 0.9, 0);
        var result = new VariationResult("hello world", true, matched, true);
        var templateReport = new TemplateReport("greet", 1, 1, 0, 0, List.of(result));
        var report = new CorpusQualityReport("ns", List.of(templateReport),
            List.of(), 1, 1, 0);

        var fixtures = VariationFixtureWriter.fromReport(report);

        assertThat(fixtures).hasSize(1);
        assertThat(fixtures.get(0).templateName()).isEqualTo("greet");
        assertThat(fixtures.get(0).input()).isEqualTo("hello world");
        assertThat(fixtures.get(0).expectMatch()).isTrue();
        assertThat(fixtures.get(0).expectedFields()).containsEntry("target", "world");
    }
}
