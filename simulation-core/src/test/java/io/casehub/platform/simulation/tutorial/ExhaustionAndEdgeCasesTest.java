package io.casehub.platform.simulation.tutorial;

import io.casehub.platform.simulation.ExhaustionPolicy;
import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.SimulationConfig;
import io.casehub.platform.simulation.SimulationConfigException;
import io.casehub.platform.simulation.SimulationExhaustedException;
import io.casehub.platform.simulation.SimulationKeyNotFoundException;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exhaustion policies, error handling, and edge cases.
 *
 * What happens when the corpus runs out? When a key isn't found?
 * When the config is wrong? This test covers the failure modes.
 */
class ExhaustionAndEdgeCasesTest {

    static final String METHOD = "my-spi.process";

    /**
     * WRAP (default): cycles back to the beginning when corpus runs out.
     * Use for load testing — infinite responses from a finite corpus.
     */
    @Test
    void wrapPolicyRecyclesCorpusIndefinitely() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.seed(METHOD, List.of(
                new InvocationRecord<>("t1", null, "in", "A", Instant.now()),
                new InvocationRecord<>("t1", null, "in", "B", Instant.now())));

        final var runtime = new SimulationRuntime(configWithExhaustion(METHOD, "sequential", ExhaustionPolicy.WRAP), corpus);
        final var strategy = runtime.<String, String>strategyFor(METHOD).orElseThrow();

        // Resolves indefinitely — wraps around
        assertThat(strategy.resolve("x")).isEqualTo("A");
        assertThat(strategy.resolve("x")).isEqualTo("B");
        assertThat(strategy.resolve("x")).isEqualTo("A");
        assertThat(strategy.resolve("x")).isEqualTo("B");
        assertThat(strategy.resolve("x")).isEqualTo("A");
    }

    /**
     * THROW: fails loudly when corpus runs out.
     * Use for scenario testing — exactly N responses expected.
     */
    @Test
    void throwPolicyFailsWhenCorpusExhausted() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.seed(METHOD, List.of(
                new InvocationRecord<>("t1", null, "in", "only-one", Instant.now())));

        final var runtime = new SimulationRuntime(
                configWithExhaustion(METHOD, "sequential", ExhaustionPolicy.THROW), corpus);
        final var strategy = runtime.<String, String>strategyFor(METHOD).orElseThrow();

        assertThat(strategy.resolve("x")).isEqualTo("only-one");
        assertThatThrownBy(() -> strategy.resolve("x"))
                .isInstanceOf(SimulationExhaustedException.class)
                .hasMessageContaining("exhausted");
    }

    /**
     * canResolve lets you check before resolving — avoids exceptions.
     */
    @Test
    void canResolveChecksWithoutConsuming() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.seed(METHOD, List.of(
                new InvocationRecord<>("t1", null, "in", "value", Instant.now())));

        final var runtime = new SimulationRuntime(
                configWithExhaustion(METHOD, "sequential", ExhaustionPolicy.THROW), corpus);
        final var strategy = runtime.<String, String>strategyFor(METHOD).orElseThrow();

        assertThat(strategy.canResolve("x")).isTrue();
        strategy.resolve("x");
        assertThat(strategy.canResolve("x")).isFalse();
    }

    /**
     * Key-lookup with missing key — throws SimulationKeyNotFoundException.
     */
    @Test
    void keyLookupMissThrowsWithKey() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.seed(METHOD, List.of(
                new InvocationRecord<>("t1", "known", "in", "out", Instant.now())));

        final var runtime = new SimulationRuntime(configFor(METHOD, "key-lookup"), corpus);
        runtime.registerExtractor(METHOD, (String input) -> input);

        final var strategy = runtime.<String, String>strategyFor(METHOD).orElseThrow();

        assertThatThrownBy(() -> strategy.resolve("unknown"))
                .isInstanceOf(SimulationKeyNotFoundException.class)
                .satisfies(ex -> assertThat(((SimulationKeyNotFoundException) ex).getKey())
                        .isEqualTo("unknown"));
    }

    /**
     * Key-lookup without registering an extractor — config error.
     */
    @Test
    void keyLookupWithoutExtractorThrowsConfigError() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        final var runtime = new SimulationRuntime(configFor(METHOD, "key-lookup"), corpus);

        // Forgot to call runtime.registerExtractor() — should fail clearly
        assertThatThrownBy(() -> runtime.strategyFor(METHOD))
                .isInstanceOf(SimulationConfigException.class)
                .hasMessageContaining("KeyExtractor");
    }

    /**
     * Unknown strategy name — fails at configuration time, not at use time.
     */
    @Test
    void unknownStrategyThrowsConfigError() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        final var runtime = new SimulationRuntime(configFor(METHOD, "quantum-entangled"), corpus);

        assertThatThrownBy(() -> runtime.strategyFor(METHOD))
                .isInstanceOf(SimulationConfigException.class)
                .hasMessageContaining("quantum-entangled");
    }

    /**
     * Strategy is cached — same instance returned on subsequent calls.
     */
    @Test
    void strategyInstanceIsCachedAndReused() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.seed(METHOD, List.of(
                new InvocationRecord<>("t1", null, "in", "out", Instant.now())));

        final var runtime = new SimulationRuntime(configFor(METHOD, "sequential"), corpus);

        final var first = runtime.<String, String>strategyFor(METHOD).orElseThrow();
        final var second = runtime.<String, String>strategyFor(METHOD).orElseThrow();
        assertThat(first).isSameAs(second);
    }

    // --- helpers ---

    static SimulationConfig configFor(final String qualifiedName, final String strategy) {
        return configWithExhaustion(qualifiedName, strategy, null);
    }

    static SimulationConfig configWithExhaustion(final String qualifiedName, final String strategy,
                                                  final ExhaustionPolicy exhaustion) {
        return new SimulationConfig() {
            @Override
            public Optional<String> strategyFor(final String qn) {
                return qn.equals(qualifiedName) ? Optional.ofNullable(strategy) : Optional.empty();
            }

            @Override
            public boolean captureEnabled(final String qn) {
                return false;
            }

            @Override
            public Optional<ExhaustionPolicy> exhaustionPolicy(final String qn) {
                return Optional.ofNullable(exhaustion);
            }
        };
    }
}
