package io.casehub.template.registry.nlp.svo;

import io.casehub.template.registry.NlpTokenizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenNlpTokenizerFactoryTest {

    @Test
    void supportedLanguagesIncludesEnglish() {
        var factory = new OpenNlpTokenizerFactory();
        assertThat(factory.supportedLanguages()).contains("en");
    }

    @Test
    void createEnglishTokenizerSucceeds() {
        var factory = new OpenNlpTokenizerFactory();
        NlpTokenizer tokenizer = factory.create("en");
        var tokens = tokenizer.tokenize("I approve this design");
        assertThat(tokens).isNotEmpty();
        assertThat(tokens.get(0).surface()).isEqualTo("I");
    }

    @Test
    void createSameLanguageTwiceReturnsCachedInstance() {
        var factory = new OpenNlpTokenizerFactory();
        NlpTokenizer first = factory.create("en");
        NlpTokenizer second = factory.create("en");
        assertThat(first).isSameAs(second);
    }

    @Test
    void createUnsupportedLanguageThrows() {
        var factory = new OpenNlpTokenizerFactory();
        assertThatThrownBy(() -> factory.create("xx"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("xx");
    }
}
