package io.casehub.platform.simulation.selector;

import io.casehub.platform.simulation.CorpusSelectorConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RandomSelectorTest {

    @Test
    void selectsPhaseByWeight() {
        var selector = new RandomSelector(
                List.of(
                        new CorpusSelectorConfig.WeightedPhase("normal", 0.8),
                        new CorpusSelectorConfig.WeightedPhase("error", 0.2)),
                new Random(42));

        var results = IntStream.range(0, 1000)
                .mapToObj(i -> selector.activePhase())
                .toList();

        long normalCount = results.stream().filter("normal"::equals).count();
        assertThat(normalCount).isBetween(700L, 900L);
    }

    @Test
    void deterministicWithFixedSeed() {
        var selector1 = new RandomSelector(
                List.of(
                        new CorpusSelectorConfig.WeightedPhase("a", 0.5),
                        new CorpusSelectorConfig.WeightedPhase("b", 0.5)),
                new Random(123));
        var selector2 = new RandomSelector(
                List.of(
                        new CorpusSelectorConfig.WeightedPhase("a", 0.5),
                        new CorpusSelectorConfig.WeightedPhase("b", 0.5)),
                new Random(123));

        for (int i = 0; i < 100; i++) {
            assertThat(selector1.activePhase()).isEqualTo(selector2.activePhase());
        }
    }

    @Test
    void singlePhaseAlwaysSelected() {
        var selector = new RandomSelector(
                List.of(new CorpusSelectorConfig.WeightedPhase("only", 1.0)),
                new Random());

        for (int i = 0; i < 100; i++) {
            assertThat(selector.activePhase()).isEqualTo("only");
        }
    }

    @Test
    void threePhases() {
        var selector = new RandomSelector(
                List.of(
                        new CorpusSelectorConfig.WeightedPhase("a", 0.5),
                        new CorpusSelectorConfig.WeightedPhase("b", 0.3),
                        new CorpusSelectorConfig.WeightedPhase("c", 0.2)),
                new Random(99));

        var results = IntStream.range(0, 1000)
                .mapToObj(i -> selector.activePhase())
                .toList();

        long aCount = results.stream().filter("a"::equals).count();
        long bCount = results.stream().filter("b"::equals).count();
        long cCount = results.stream().filter("c"::equals).count();

        assertThat(aCount).isBetween(400L, 600L);
        assertThat(bCount).isBetween(200L, 400L);
        assertThat(cCount).isBetween(100L, 300L);
    }
}
