package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoopDirectiveContinuousTest {

    @Test
    void parsesContinuousString() {
        var directive = LoopDirective.parse("continuous");
        assertThat(directive).isInstanceOf(LoopDirective.Continuous.class);
    }

    @Test
    void parsesContinuousStringCaseInsensitive() {
        var directive = LoopDirective.parse("CONTINUOUS");
        assertThat(directive).isInstanceOf(LoopDirective.Continuous.class);
    }

    @Test
    void parsesContinuousInMap() {
        var directive = LoopDirective.parse(Map.of("continuous", true));
        assertThat(directive).isInstanceOf(LoopDirective.Continuous.class);
    }

    @Test
    void existingCountStillWorks() {
        var directive = LoopDirective.parse(3);
        assertThat(directive).isInstanceOf(LoopDirective.Count.class);
        assertThat(((LoopDirective.Count) directive).count()).isEqualTo(3);
    }

    @Test
    void existingUntilStillWorks() {
        var directive = LoopDirective.parse(Map.of("until", "done"));
        assertThat(directive).isInstanceOf(LoopDirective.Until.class);
    }
}
