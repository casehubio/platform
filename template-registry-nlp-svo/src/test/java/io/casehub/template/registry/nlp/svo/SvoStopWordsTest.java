package io.casehub.template.registry.nlp.svo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SvoStopWordsTest {

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr", "es", "it", "pt"})
    void allSvoLanguagesHaveStopWords(String lang) {
        var words = SvoStopWords.forLanguage(lang);
        assertThat(words).isNotEmpty();
        assertThat(words.size()).isGreaterThan(20);
    }

    @Test
    void englishContainsExpectedWords() {
        var words = SvoStopWords.forLanguage("en");
        assertThat(words).contains("the", "a", "is", "are", "i", "you");
    }

    @Test
    void frenchContainsExpectedWords() {
        var words = SvoStopWords.forLanguage("fr");
        assertThat(words).contains("le", "la", "les", "de", "du", "des", "un", "une");
    }

    @Test
    void unsupportedLanguageThrows() {
        assertThatThrownBy(() -> SvoStopWords.forLanguage("zz"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
