package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoopDirectiveTest {

    @Test
    void parse_integer_returnsCount() {
        var result = LoopDirective.parse(5);
        assertThat(result).isInstanceOf(LoopDirective.Count.class);
        assertThat(((LoopDirective.Count) result).count()).isEqualTo(5);
    }

    @Test
    void parse_mapWithCount_returnsCount() {
        var result = LoopDirective.parse(Map.of("count", 5));
        assertThat(result).isInstanceOf(LoopDirective.Count.class);
        assertThat(((LoopDirective.Count) result).count()).isEqualTo(5);
    }

    @Test
    void parse_mapWithCountAndUntil_returnsCountUntil() {
        var result = LoopDirective.parse(Map.of("count", 10, "until", "${done}"));
        assertThat(result).isInstanceOf(LoopDirective.CountUntil.class);
        var cu = (LoopDirective.CountUntil) result;
        assertThat(cu.count()).isEqualTo(10);
        assertThat(cu.until()).isEqualTo("${done}");
    }

    @Test
    void parse_mapWithUntilOnly_returnsUntil() {
        var result = LoopDirective.parse(Map.of("until", "${converged}"));
        assertThat(result).isInstanceOf(LoopDirective.Until.class);
        assertThat(((LoopDirective.Until) result).until()).isEqualTo("${converged}");
    }

    @Test
    void parse_null_throws() {
        assertThatThrownBy(() -> LoopDirective.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_invalidType_throws() {
        assertThatThrownBy(() -> LoopDirective.parse(true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Boolean");
    }

    @Test
    void parse_emptyMap_throws() {
        assertThatThrownBy(() -> LoopDirective.parse(Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_zeroCount_throws() {
        assertThatThrownBy(() -> LoopDirective.parse(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(">= 1");
    }

    @Test
    void parse_existingDirective_returnsItself() {
        var original = new LoopDirective.Count(3);
        assertThat(LoopDirective.parse(original)).isSameAs(original);
    }
}
