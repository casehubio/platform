package io.casehub.platform.simulation.tutorial;

import io.casehub.platform.simulation.ExhaustionPolicy;
import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.NoOpSimulationCorpus;
import io.casehub.platform.simulation.SimulationConfig;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Start here — simplest possible simulation setup.
 *
 * Three concepts: corpus (data), strategy (how to pick a response),
 * runtime (wires them together). This test shows each one.
 */
class SimulationGettingStartedTest {

    // Every simulated method needs a qualifiedName: "spi-name.method-name"
    static final String GREET = "greeting-service.greet";

    /**
     * Simplest case: seed a corpus, pick sequential strategy, resolve.
     */
    @Test
    void seedCorpusThenResolveSequentially() {
        // 1. Create a corpus and seed it with known responses
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.seed(GREET, List.of(
                record("Alice", "Hello Alice!"),
                record("Bob", "Hello Bob!")));

        // 2. Wire the runtime: "sequential" for our method
        final var runtime = new SimulationRuntime(configFor(GREET, "sequential"), corpus);

        // 3. Resolve — returns entries in insertion order, wraps at end
        final var strategy = runtime.<String, String>strategyFor(GREET).orElseThrow();
        assertThat(strategy.resolve("anyone")).isEqualTo("Hello Alice!");
        assertThat(strategy.resolve("anyone")).isEqualTo("Hello Bob!");
        assertThat(strategy.resolve("anyone")).isEqualTo("Hello Alice!"); // wraps
    }

    /**
     * Key-lookup: same input always returns same output — deterministic.
     */
    @Test
    void keyLookupForDeterministicResponses() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.seed(GREET, List.of(
                record("alice", "Alice", "Hello Alice!"),
                record("bob", "Bob", "Hello Bob!")));

        final var runtime = new SimulationRuntime(configFor(GREET, "key-lookup"), corpus);

        // Register how to derive a lookup key from the input
        runtime.registerExtractor(GREET, (String name) -> name.toLowerCase());

        final var strategy = runtime.<String, String>strategyFor(GREET).orElseThrow();
        assertThat(strategy.resolve("Alice")).isEqualTo("Hello Alice!");
        assertThat(strategy.resolve("Bob")).isEqualTo("Hello Bob!");
        assertThat(strategy.resolve("Alice")).isEqualTo("Hello Alice!"); // deterministic
    }

    /**
     * Random with a seeded Random — reproducible sampling from corpus.
     */
    @Test
    void randomWithSeedForReproducibleSampling() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.seed(GREET, List.of(
                record("Alice", "Hello Alice!"),
                record("Bob", "Hello Bob!"),
                record("Carol", "Hello Carol!")));

        final var runtime = new SimulationRuntime(configFor(GREET, "random"), corpus);

        final var strategy = runtime.<String, String>strategyFor(GREET).orElseThrow();
        final String result = strategy.resolve("anyone");
        assertThat(result).isIn("Hello Alice!", "Hello Bob!", "Hello Carol!");
    }

    /**
     * No strategy configured = the decorator passes through to the real impl.
     */
    @Test
    void noStrategyMeansPassthrough() {
        final var runtime = new SimulationRuntime(
                configFor(GREET, null), new NoOpSimulationCorpus<>());
        assertThat(runtime.<String, String>strategyFor(GREET)).isEmpty();
    }

    // --- helpers reused across tutorial tests ---

    static InvocationRecord<String, String> record(final String input, final String output) {
        return new InvocationRecord<>("tenant-1", null, input, output, Instant.now());
    }

    static InvocationRecord<String, String> record(final String key, final String input, final String output) {
        return new InvocationRecord<>("tenant-1", key, input, output, Instant.now());
    }

    static SimulationConfig configFor(final String qualifiedName, final String strategyName) {
        return new SimulationConfig() {
            @Override
            public Optional<String> strategyFor(final String qn) {
                return qn.equals(qualifiedName) ? Optional.ofNullable(strategyName) : Optional.empty();
            }

            @Override
            public boolean captureEnabled(final String qn) {
                return false;
            }

            @Override
            public Optional<ExhaustionPolicy> exhaustionPolicy(final String qn) {
                return Optional.empty();
            }
        };
    }
}
