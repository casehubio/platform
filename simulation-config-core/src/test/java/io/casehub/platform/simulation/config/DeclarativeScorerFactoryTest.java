package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.SimulationConfigException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class DeclarativeScorerFactoryTest {

    private final DeclarativeScorerFactory factory = new DeclarativeScorerFactory();

    @Test
    void singleExactField() {
        var scorer = factory.create("fields:domain:exact:1.0");

        double match = scorer.score(
                Map.of("domain", "test"),
                Map.of("domain", "test"));

        assertThat(match).isEqualTo(1.0);
    }

    @Test
    void multipleFieldsParsed() {
        var scorer = factory.create("fields:domain:exact:1.0,question:substring:0.5");

        double score = scorer.score(
                Map.of("domain", "test", "question", "how?"),
                Map.of("domain", "test", "question", "what?"));

        assertThat(score).isCloseTo(0.667, within(0.01));
    }

    @Test
    void numericRangeField() {
        var scorer = factory.create("fields:amount:numeric-range:1.0");

        double score = scorer.score(
                Map.of("amount", 100),
                Map.of("amount", 100));

        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void ignoreField() {
        var scorer = factory.create("fields:id:ignore:0.0,name:exact:1.0");

        double score = scorer.score(
                Map.of("id", "abc", "name", "test"),
                Map.of("id", "xyz", "name", "test"));

        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void unknownScorerThrows() {
        assertThatThrownBy(() -> factory.create("fields:name:unknown:1.0"))
                .isInstanceOf(SimulationConfigException.class);
    }

    @Test
    void unknownFormatThrows() {
        assertThatThrownBy(() -> factory.create("invalid-spec"))
                .isInstanceOf(SimulationConfigException.class);
    }
}
