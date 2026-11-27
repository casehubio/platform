package io.casehub.template.registry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CorpusQualityReportTest {

    @Test
    void variationResult_passedWhenExpectedMatchAndCorrectTemplate() {
        var matched = new TemplateMatch.Matched("ns", "vote", 0, Map.of("decision", "approve"), 0.9, 0);
        var result = new VariationResult("I approve this", true, matched, true);

        assertThat(result.passed()).isTrue();
        assertThat(result.expectedMatch()).isTrue();
    }

    @Test
    void variationResult_passedWhenExpectedNoMatchAndUnmatched() {
        var unmatched = new TemplateMatch.Unmatched("random text");
        var result = new VariationResult("random text", false, unmatched, true);

        assertThat(result.passed()).isTrue();
        assertThat(result.expectedMatch()).isFalse();
    }

    @Test
    void variationResult_failedWhenExpectedMatchButUnmatched() {
        var unmatched = new TemplateMatch.Unmatched("I approve this");
        var result = new VariationResult("I approve this", true, unmatched, false);

        assertThat(result.passed()).isFalse();
    }

    @Test
    void templateReport_matchRateCalculation() {
        var matched = new TemplateMatch.Matched("ns", "vote", 0, Map.of(), 0.9, 0);
        var unmatched = new TemplateMatch.Unmatched("miss");
        var report = new TemplateReport("vote", 4, 3, 0, 0, List.of(
            new VariationResult("a", true, matched, true),
            new VariationResult("b", true, matched, true),
            new VariationResult("c", true, matched, true),
            new VariationResult("d", true, unmatched, false)
        ));

        assertThat(report.matchRate()).isEqualTo(0.75);
    }

    @Test
    void templateReport_matchRateOnePointZeroWhenNoVariations() {
        var report = new TemplateReport("empty", 0, 0, 0, 0, List.of());
        assertThat(report.matchRate()).isEqualTo(1.0);
    }

    @Test
    void corpusQualityReport_matchRateCalculation() {
        var report = new CorpusQualityReport("ns", List.of(), List.of(), 10, 8, 0);
        assertThat(report.matchRate()).isEqualTo(0.8);
    }

    @Test
    void corpusQualityReport_matchRateOnePointZeroWhenNoVariations() {
        var report = new CorpusQualityReport("ns", List.of(), List.of(), 0, 0, 0);
        assertThat(report.matchRate()).isEqualTo(1.0);
    }

    @Test
    void corpusQualityReport_collectionsAreUnmodifiable() {
        var report = new CorpusQualityReport("ns", List.of(), List.of(), 0, 0, 0);
        assertThat(report.templates()).isUnmodifiable();
        assertThat(report.collisions()).isUnmodifiable();
    }

    @Test
    void templateReport_collectionsAreUnmodifiable() {
        var report = new TemplateReport("t", 0, 0, 0, 0, List.of());
        assertThat(report.variations()).isUnmodifiable();
    }
}
