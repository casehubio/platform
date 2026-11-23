package io.casehub.extraction.nlp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenNlpTokenizerTest {

    private final OpenNlpTokenizer tokenizer = new OpenNlpTokenizer();

    @Test
    void tokenizesSimpleSentence() {
        var tokens = tokenizer.tokenize("I approve this design");
        assertThat(tokens).hasSizeGreaterThanOrEqualTo(4);
        assertThat(tokens.get(0).surface()).isEqualTo("I");
        assertThat(tokens.get(0).pos()).isNotEmpty();
        assertThat(tokens.get(0).lemma()).isNotEmpty();
    }

    @Test
    void producesCorrectOffsets() {
        var tokens = tokenizer.tokenize("Hello world");
        assertThat(tokens.get(0).start()).isEqualTo(0);
        assertThat(tokens.get(0).end()).isEqualTo(5);
    }

    @Test
    void lemmatizesVerbs() {
        var tokens = tokenizer.tokenize("I approved this design");
        var approved = tokens.stream()
                .filter(t -> t.surface().equals("approved"))
                .findFirst().orElseThrow();
        assertThat(approved.lemma()).isEqualTo("approve");
    }

    @Test
    void lemmatizesPlurals() {
        var tokens = tokenizer.tokenize("The items are ready");
        var items = tokens.stream()
                .filter(t -> t.surface().equals("items"))
                .findFirst().orElseThrow();
        assertThat(items.lemma()).isEqualTo("item");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> tokenizer.tokenize(null))
                .isInstanceOf(NullPointerException.class);
    }
}
