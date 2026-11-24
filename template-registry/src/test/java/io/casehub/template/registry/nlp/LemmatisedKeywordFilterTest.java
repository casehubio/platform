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
        assertThat(keywords).contains("escalating", "due");
        assertThat(keywords).doesNotContain("to");
    }
}
