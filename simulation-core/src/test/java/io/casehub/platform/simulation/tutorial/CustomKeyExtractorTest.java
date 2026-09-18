package io.casehub.platform.simulation.tutorial;

import io.casehub.platform.simulation.ExhaustionPolicy;
import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.KeyExtractor;
import io.casehub.platform.simulation.SimulationConfig;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Writing KeyExtractors — how to match inputs to corpus entries.
 *
 * <p>A KeyExtractor turns a method's input into a lookup key.
 * The key-lookup and recorded-replay strategies use it to find the
 * right corpus entry deterministically. This is where you define
 * "what makes two invocations equivalent."
 */
class CustomKeyExtractorTest {

    static final String LOOKUP = "patient-service.findPatient";

    /**
     * Identity extractor — the input IS the key. Simplest case.
     */
    @Test
    void identityExtractorInputIsKey() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.seed(LOOKUP, List.of(
                new InvocationRecord<>("t1", "P-001", "P-001", "Alice Smith", Instant.now()),
                new InvocationRecord<>("t1", "P-002", "P-002", "Bob Jones", Instant.now())));

        final var runtime = new SimulationRuntime(configFor(LOOKUP, "key-lookup"), corpus);
        runtime.registerExtractor(LOOKUP, (String patientId) -> patientId);

        final var strategy = runtime.<String, String>strategyFor(LOOKUP).orElseThrow();
        assertThat(strategy.resolve("P-001")).isEqualTo("Alice Smith");
        assertThat(strategy.resolve("P-002")).isEqualTo("Bob Jones");
    }

    /**
     * Normalizing extractor — strips noise for stable matching.
     * UUIDs, timestamps, whitespace — anything non-deterministic.
     */
    @Test
    void normalizingExtractorStripsNoise() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.seed(LOOKUP, List.of(
                new InvocationRecord<>("t1", "find patient by name=alice", "find patient by name=alice ref=abc-123", "Alice Smith", Instant.now())));

        final KeyExtractor<String> normalize = input ->
                input.replaceAll("\\s+ref=[a-z0-9-]+", "").toLowerCase().trim();

        final var runtime = new SimulationRuntime(configFor(LOOKUP, "key-lookup"), corpus);
        runtime.registerExtractor(LOOKUP, normalize);

        final var strategy = runtime.<String, String>strategyFor(LOOKUP).orElseThrow();

        // Different ref= values, same normalized key → same response
        assertThat(strategy.resolve("Find patient by name=Alice ref=xyz-789"))
                .isEqualTo("Alice Smith");
    }

    /**
     * Composite extractor for multi-param methods — combine fields into a key.
     *
     * When your SPI method has multiple parameters, pass them as Object[]
     * and extract the key from the relevant fields.
     */
    @Test
    void compositeExtractorForMultipleParams() {
        final var corpus = new InMemorySimulationCorpus<Object[], String>();
        corpus.seed(LOOKUP, List.of(
                new InvocationRecord<>("t1", "department=cardiology:severity=high",
                        new Object[]{"cardiology", "high"}, "Dr. Heart", Instant.now()),
                new InvocationRecord<>("t1", "department=neurology:severity=low",
                        new Object[]{"neurology", "low"}, "Dr. Brain", Instant.now())));

        final KeyExtractor<Object[]> composite = args ->
                "department=" + args[0] + ":severity=" + args[1];

        final var runtime = new SimulationRuntime(configFor(LOOKUP, "key-lookup"), corpus);
        runtime.registerExtractor(LOOKUP, composite);

        final var strategy = runtime.<Object[], String>strategyFor(LOOKUP).orElseThrow();
        assertThat(strategy.resolve(new Object[]{"cardiology", "high"})).isEqualTo("Dr. Heart");
        assertThat(strategy.resolve(new Object[]{"neurology", "low"})).isEqualTo("Dr. Brain");
    }

    /**
     * Case-insensitive extractor — common for user-facing lookups.
     */
    @Test
    void caseInsensitiveExtractor() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.seed(LOOKUP, List.of(
                new InvocationRecord<>("t1", "alice", "alice", "Alice Smith", Instant.now())));

        final var runtime = new SimulationRuntime(configFor(LOOKUP, "key-lookup"), corpus);
        runtime.registerExtractor(LOOKUP, (String input) -> input.toLowerCase());

        final var strategy = runtime.<String, String>strategyFor(LOOKUP).orElseThrow();
        assertThat(strategy.resolve("Alice")).isEqualTo("Alice Smith");
        assertThat(strategy.resolve("ALICE")).isEqualTo("Alice Smith");
        assertThat(strategy.resolve("alice")).isEqualTo("Alice Smith");
    }

    // --- helper ---

    static SimulationConfig configFor(final String qualifiedName, final String strategy) {
        return new SimulationConfig() {
            @Override
            public Optional<String> strategyFor(final String qn) {
                return qn.equals(qualifiedName) ? Optional.of(strategy) : Optional.empty();
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
