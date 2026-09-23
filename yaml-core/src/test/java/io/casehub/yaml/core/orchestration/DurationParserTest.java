package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurationParserTest {

    @Test
    void parsesMilliseconds() {
        assertThat(DurationParser.parse("500ms")).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void parsesSeconds() {
        assertThat(DurationParser.parse("5s")).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void parsesMinutes() {
        assertThat(DurationParser.parse("2m")).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void parsesHours() {
        assertThat(DurationParser.parse("1h")).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void zeroDuration_allowed() {
        assertThat(DurationParser.parse("0s")).isEqualTo(Duration.ZERO);
    }

    @Test
    void invalidSuffix_throws() {
        assertThatThrownBy(() -> DurationParser.parse("5x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid duration");
    }

    @Test
    void noSuffix_throws() {
        assertThatThrownBy(() -> DurationParser.parse("100"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whitespace_trimmed() {
        assertThat(DurationParser.parse("  3s  ")).isEqualTo(Duration.ofSeconds(3));
    }
}
