package io.casehub.template.registry.nlp.svo;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LemmatisedKeywordFilterTest {

    private final OpenNlpTokenizer tokenizer = new OpenNlpTokenizer();
    private static final Set<String> EN_STOP_WORDS = SvoStopWords.forLanguage("en");

    @Test
    void extractsContentLemmas() {
        var keywords = LemmatisedKeywordFilter.extract(
            "Escalating quickly to ${target}", tokenizer, EN_STOP_WORDS);
        assertThat(keywords).isNotEmpty();
        assertThat(keywords).doesNotContain("to");
    }

    @Test
    void stripsVariablesAndBrackets() {
        var keywords = LemmatisedKeywordFilter.extract(
            "Escalating to ${target}[ due to ${reason}]", tokenizer, EN_STOP_WORDS);
        assertThat(keywords).doesNotContain("${target}", "${reason}");
    }

    @Test
    void morphologicalAffinity() {
        var keywords = LemmatisedKeywordFilter.extract(
            "Escalating to ${target}", tokenizer, EN_STOP_WORDS);
        var inputTokens = tokenizer.tokenize("I escalated to the manager");
        double affinity = LemmatisedKeywordFilter.affinity(keywords, inputTokens, EN_STOP_WORDS);
        assertThat(affinity).isGreaterThan(0.0);
    }

    @Test
    void noAffinityForUnrelatedInput() {
        var keywords = LemmatisedKeywordFilter.extract(
            "Escalating to ${target}", tokenizer, EN_STOP_WORDS);
        var inputTokens = tokenizer.tokenize("The weather is nice today");
        double affinity = LemmatisedKeywordFilter.affinity(keywords, inputTokens, EN_STOP_WORDS);
        assertThat(affinity).isEqualTo(0.0);
    }

    @Test
    void emptyTemplateReturnsEmptyKeywords() {
        var keywords = LemmatisedKeywordFilter.extract("${x}", tokenizer, EN_STOP_WORDS);
        assertThat(keywords).isEmpty();
    }

    @Test
    void emptyKeywordsGiveZeroAffinity() {
        var inputTokens = tokenizer.tokenize("Some input text");
        double affinity = LemmatisedKeywordFilter.affinity(
            Set.of(), inputTokens, EN_STOP_WORDS);
        assertThat(affinity).isEqualTo(0.0);
    }

    @Test
    void affinityWithSynonyms_expandsMatchSet() {
        var keywords = LemmatisedKeywordFilter.extract(
                "Raising ${target} to ${level}", tokenizer, EN_STOP_WORDS);
        var inputTokens = tokenizer.tokenize("I escalated the manager");
        var synonyms = Map.of("raise", Set.of("escalate"));

        double affinity = LemmatisedKeywordFilter.affinity(keywords, inputTokens, synonyms);
        assertThat(affinity).isGreaterThan(0.0);
    }

    @Test
    void affinityWithSynonyms_emptySynonymsBehavesLikeOriginal() {
        var keywords = LemmatisedKeywordFilter.extract(
                "Raising ${target} to ${level}", tokenizer, EN_STOP_WORDS);
        var inputTokens = tokenizer.tokenize("I escalated the manager");

        double withStopWords = LemmatisedKeywordFilter.affinity(keywords, inputTokens, EN_STOP_WORDS);
        double withEmptySynonyms = LemmatisedKeywordFilter.affinity(keywords, inputTokens, Map.of());
        assertThat(withEmptySynonyms).isEqualTo(withStopWords);
    }

    @Test
    void affinityWithSynonyms_multipleSynonymsForSameKeyword() {
        var keywords = Set.of("raise");
        var inputTokens = tokenizer.tokenize("I promoted someone");
        var synonyms = Map.of("raise", Set.of("escalate", "promote"));

        double affinity = LemmatisedKeywordFilter.affinity(keywords, inputTokens, synonyms);
        assertThat(affinity).isEqualTo(1.0);
    }
}
