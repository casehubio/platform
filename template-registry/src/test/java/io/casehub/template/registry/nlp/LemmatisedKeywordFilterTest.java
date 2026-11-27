package io.casehub.template.registry.nlp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LemmatisedKeywordFilterTest {

    private final OpenNlpTokenizer tokenizer = new OpenNlpTokenizer();

    @Test
    void extractsContentLemmas() {
        var keywords = LemmatisedKeywordFilter.extract(
            "Escalating quickly to ${target}", tokenizer);
        assertThat(keywords).isNotEmpty();
        assertThat(keywords).doesNotContain("to");
    }

    @Test
    void stripsVariablesAndBrackets() {
        var keywords = LemmatisedKeywordFilter.extract(
            "Escalating to ${target}[ due to ${reason}]", tokenizer);
        assertThat(keywords).doesNotContain("${target}", "${reason}");
    }

    @Test
    void morphologicalAffinity() {
        var keywords = LemmatisedKeywordFilter.extract(
            "Escalating to ${target}", tokenizer);
        var inputTokens = tokenizer.tokenize("I escalated to the manager");
        double affinity = LemmatisedKeywordFilter.affinity(keywords, inputTokens);
        assertThat(affinity).isGreaterThan(0.0);
    }

    @Test
    void noAffinityForUnrelatedInput() {
        var keywords = LemmatisedKeywordFilter.extract(
            "Escalating to ${target}", tokenizer);
        var inputTokens = tokenizer.tokenize("The weather is nice today");
        double affinity = LemmatisedKeywordFilter.affinity(keywords, inputTokens);
        assertThat(affinity).isEqualTo(0.0);
    }

    @Test
    void emptyTemplateReturnsEmptyKeywords() {
        var keywords = LemmatisedKeywordFilter.extract("${x}", tokenizer);
        assertThat(keywords).isEmpty();
    }

    @Test
    void emptyKeywordsGiveZeroAffinity() {
        var inputTokens = tokenizer.tokenize("Some input text");
        double affinity = LemmatisedKeywordFilter.affinity(
            java.util.Set.of(), inputTokens);
        assertThat(affinity).isEqualTo(0.0);
    }

    @Test
    void rawExtractionWithoutNlp() {
        var keywords = LemmatisedKeywordFilter.extractRaw(
            "Escalating to ${target}[ due to ${reason}]");
        assertThat(keywords).contains("escalating");
        assertThat(keywords).doesNotContain("to", "due");
    }

    @Test
    void affinityWithSynonyms_expandsMatchSet() {
        var keywords = LemmatisedKeywordFilter.extract(
                "Raising ${target} to ${level}", tokenizer);
        var inputTokens = tokenizer.tokenize("I escalated the manager");
        var synonyms    = java.util.Map.of("raise", java.util.Set.of("escalate"));

        double affinity = LemmatisedKeywordFilter.affinity(keywords, inputTokens, synonyms);
        assertThat(affinity).isGreaterThan(0.0);
    }

    @Test
    void affinityWithSynonyms_emptySynonymsBehavesLikeOriginal() {
        var keywords = LemmatisedKeywordFilter.extract(
                "Raising ${target} to ${level}", tokenizer);
        var inputTokens = tokenizer.tokenize("I escalated the manager");

        double withoutSynonyms   = LemmatisedKeywordFilter.affinity(keywords, inputTokens);
        double withEmptySynonyms = LemmatisedKeywordFilter.affinity(keywords, inputTokens, java.util.Map.of());
        assertThat(withEmptySynonyms).isEqualTo(withoutSynonyms);
    }

    @Test
    void affinityWithSynonyms_multipleSynonymsForSameKeyword() {
        var keywords    = java.util.Set.of("raise");
        var inputTokens = tokenizer.tokenize("I promoted someone");
        var synonyms    = java.util.Map.of("raise", java.util.Set.of("escalate", "promote"));

        double affinity = LemmatisedKeywordFilter.affinity(keywords, inputTokens, synonyms);
        assertThat(affinity).isEqualTo(1.0);
    }

}
