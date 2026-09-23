package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryDirectiveTest {

    @Test
    void parse_integer_returnsSimple() {
        var result = RetryDirective.parse(3);
        assertThat(result).isInstanceOf(RetryDirective.Simple.class);
        assertThat(((RetryDirective.Simple) result).max()).isEqualTo(3);
    }

    @Test
    void parse_mapWithMaxOnly_returnsSimple() {
        var result = RetryDirective.parse(Map.of("max", 5));
        assertThat(result).isInstanceOf(RetryDirective.Simple.class);
        assertThat(((RetryDirective.Simple) result).max()).isEqualTo(5);
    }

    @Test
    void parse_mapWithAllFields_returnsFull() {
        var result = RetryDirective.parse(Map.of("max", 3, "backoff", "exponential", "delay", "1s"));
        assertThat(result).isInstanceOf(RetryDirective.Full.class);
        var full = (RetryDirective.Full) result;
        assertThat(full.max()).isEqualTo(3);
        assertThat(full.backoff()).isEqualTo("exponential");
        assertThat(full.delay()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void parse_mapWithMillisDelay_parsesCorrectly() {
        var result = RetryDirective.parse(Map.of("max", 2, "backoff", "fixed", "delay", "500ms"));
        assertThat(result).isInstanceOf(RetryDirective.Full.class);
        assertThat(((RetryDirective.Full) result).delay()).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void parse_null_throws() {
        assertThatThrownBy(() -> RetryDirective.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_zeroMax_throws() {
        assertThatThrownBy(() -> RetryDirective.parse(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(">= 1");
    }

    @Test
    void parse_mapMissingMax_throws() {
        assertThatThrownBy(() -> RetryDirective.parse(Map.of("backoff", "exponential")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max");
    }

    @Test
    void parse_existingDirective_returnsItself() {
        var original = new RetryDirective.Simple(2);
        assertThat(RetryDirective.parse(original)).isSameAs(original);
    }
}
