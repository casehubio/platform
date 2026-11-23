package io.casehub.platform.coordination.protocol;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateKeywordsTest {

    @Test
    void extractsLiteralWordsFromTemplate() {
        Set<String> keywords = TemplateKeywords.extract("I ${decision} this design because ${rationale}");
        assertThat(keywords).containsExactlyInAnyOrder("design", "because");
    }

    @Test
    void filtersStopWords() {
        Set<String> keywords = TemplateKeywords.extract("the ${name} is at ${place}");
        assertThat(keywords).isEmpty();
    }

    @Test
    void handlesOptionalBrackets() {
        Set<String> keywords = TemplateKeywords.extract("${action}[ by ${actor}][ at ${time}]");
        assertThat(keywords).isEmpty();
    }

    @Test
    void extractsMeaningfulWords() {
        Set<String> keywords = TemplateKeywords.extract("I escalate to ${target}");
        assertThat(keywords).containsExactlyInAnyOrder("escalate");
    }

    @Test
    void lowercasesKeywords() {
        Set<String> keywords = TemplateKeywords.extract("APPROVE ${item} NOW");
        assertThat(keywords).containsExactlyInAnyOrder("approve", "now");
    }

    @Test
    void emptyTemplateReturnsEmptyKeywords() {
        assertThat(TemplateKeywords.extract("")).isEmpty();
    }

    @Test
    void templateWithOnlyVariablesReturnsEmpty() {
        assertThat(TemplateKeywords.extract("${a} ${b} ${c}")).isEmpty();
    }

    @Test
    void affinityWithFullOverlap() {
        Set<String> keywords = Set.of("design", "approve");
        double score = TemplateKeywords.affinity(keywords, "I approve this design");
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void affinityWithPartialOverlap() {
        Set<String> keywords = Set.of("design", "approve");
        double score = TemplateKeywords.affinity(keywords, "I approve the architecture");
        assertThat(score).isEqualTo(0.5);
    }

    @Test
    void affinityWithNoOverlap() {
        Set<String> keywords = Set.of("design", "approve");
        double score = TemplateKeywords.affinity(keywords, "schedule a meeting");
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void affinityIsCaseInsensitive() {
        Set<String> keywords = Set.of("design", "approve");
        double score = TemplateKeywords.affinity(keywords, "APPROVE this DESIGN");
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void affinityWithEmptyKeywordsReturnsZero() {
        assertThat(TemplateKeywords.affinity(Set.of(), "some input")).isEqualTo(0.0);
    }

    @Test
    void affinityWithEmptyInputReturnsZero() {
        assertThat(TemplateKeywords.affinity(Set.of("design"), "")).isEqualTo(0.0);
    }
}
