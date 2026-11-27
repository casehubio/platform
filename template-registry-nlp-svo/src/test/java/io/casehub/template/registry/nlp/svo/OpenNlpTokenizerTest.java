package io.casehub.template.registry.nlp.svo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenNlpTokenizerTest {

    private final OpenNlpTokenizer tokenizer = new OpenNlpTokenizer();

    @Test
    void tokenizesSimpleSentence() {
        var tokens = tokenizer.tokenize("I approve this design");
        assertThat(tokens).hasSizeGreaterThanOrEqualTo(4);
        assertThat(tokens.get(0).surface()).isEqualTo("I");
    }

    @Test
    void lemmatizesVerb() {
        var tokens = tokenizer.tokenize("She approved the architecture");
        var approved = tokens.stream()
            .filter(t -> t.surface().equals("approved"))
            .findFirst().orElseThrow();
        assertThat(approved.lemma()).isEqualTo("approve");
    }

    @Test
    void posTagsPresent() {
        var tokens = tokenizer.tokenize("I approve this design");
        assertThat(tokens).allSatisfy(t -> {
            assertThat(t.pos()).isNotEmpty();
            assertThat(t.start()).isGreaterThanOrEqualTo(0);
            assertThat(t.end()).isGreaterThan(t.start());
        });
    }

    @Test
    void emptyInputReturnsEmptyList() {
        assertThat(tokenizer.tokenize("")).isEmpty();
        assertThat(tokenizer.tokenize("   ")).isEmpty();
    }

    @Test
    void pluralNoun() {
        var tokens = tokenizer.tokenize("The designs are ready");
        var designs = tokens.stream()
            .filter(t -> t.surface().equals("designs"))
            .findFirst().orElseThrow();
        assertThat(designs.lemma()).isEqualTo("design");
    }
}
