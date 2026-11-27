package io.casehub.template.registry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NlpTokenizerFactoryTest {

    @Test
    void createReturnsTokenizerForSupportedLanguage() {
        NlpTokenizerFactory factory = new NlpTokenizerFactory() {
            @Override
            public NlpTokenizer create(String languageTag) {
                if ("en".equals(languageTag)) {
                    return text -> List.of(new AnnotatedToken(text, text.toLowerCase(), "NN", 0, text.length()));
                }
                throw new UnsupportedOperationException("Unsupported: " + languageTag);
            }
            @Override
            public Set<String> supportedLanguages() { return Set.of("en"); }
        };
        assertThat(factory.supportedLanguages()).containsExactly("en");
        NlpTokenizer tokenizer = factory.create("en");
        assertThat(tokenizer.tokenize("Hello")).hasSize(1);
    }

    @Test
    void createThrowsForUnsupportedLanguage() {
        NlpTokenizerFactory factory = new NlpTokenizerFactory() {
            @Override
            public NlpTokenizer create(String languageTag) {
                throw new UnsupportedOperationException("Unsupported: " + languageTag);
            }
            @Override
            public Set<String> supportedLanguages() { return Set.of(); }
        };
        assertThatThrownBy(() -> factory.create("zz"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
