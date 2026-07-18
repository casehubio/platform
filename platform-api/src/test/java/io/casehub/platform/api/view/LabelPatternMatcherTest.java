package io.casehub.platform.api.view;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LabelPatternMatcherTest {

    @ParameterizedTest
    @CsvSource({
        "legal, legal, true",
        "legal, other, false",
        "legal, legal/contracts, false",
        "legal/*, legal/contracts, true",
        "legal/*, legal/contracts/nda, false",
        "legal/*, legal, false",
        "legal/**, legal/contracts, true",
        "legal/**, legal/contracts/nda, true",
        "legal/**, legal, false",
    })
    void matches(String pattern, String path, boolean expected) {
        assertThat(LabelPatternMatcher.matches(pattern, path)).isEqualTo(expected);
    }

    @Test
    void nullPatternThrows() {
        assertThrows(NullPointerException.class,
            () -> LabelPatternMatcher.matches(null, "legal"));
    }

    @Test
    void nullPathThrows() {
        assertThrows(NullPointerException.class,
            () -> LabelPatternMatcher.matches("legal", null));
    }

    @Test
    void emptyPatternMatchesEmptyPath() {
        assertThat(LabelPatternMatcher.matches("", "")).isTrue();
        assertThat(LabelPatternMatcher.matches("", "legal")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "a/b/*, a/b/c, true",
        "a/b/*, a/b/c/d, false",
        "a/b/**, a/b/c, true",
        "a/b/**, a/b/c/d, true",
        "a/b/**, a/b/c/d/e, true",
    })
    void deepPaths(String pattern, String path, boolean expected) {
        assertThat(LabelPatternMatcher.matches(pattern, path)).isEqualTo(expected);
    }
}
