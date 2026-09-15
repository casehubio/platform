package io.casehub.platform.simulation.tutorial;

import io.casehub.platform.simulation.ExhaustionPolicy;
import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.SimulationConfig;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capture mode: record real SPI calls, then replay them later.
 *
 * <p>This is the simulation lifecycle: capture real traffic, build a corpus,
 * then use that corpus for simulation. Capture and simulation can run
 * independently or together.
 */
class CaptureAndReplayTest {

    static final String TRANSLATE = "translation-service.translate";

    /**
     * Step 1: Capture real invocations to build a corpus.
     *
     * In a real system, the generated @Decorator does this automatically
     * when capture is enabled. Here we show it manually.
     */
    @Test
    void captureBuildsCorpusFromRealInvocations() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        final var config = captureConfig(TRANSLATE);
        final var runtime = new SimulationRuntime(config, corpus);

        // Simulate what the generated decorator does after delegating:
        // if (simulation.captureEnabled(qn)) simulation.capture(qn, tenantId, input, output);
        assertThat(runtime.captureEnabled(TRANSLATE)).isTrue();

        runtime.capture(TRANSLATE, "tenant-1", "hello", "hola");
        runtime.capture(TRANSLATE, "tenant-1", "goodbye", "adiós");
        runtime.capture(TRANSLATE, "tenant-1", "thank you", "gracias");

        // Corpus now has 3 recorded invocations
        assertThat(corpus.size(TRANSLATE)).isEqualTo(3);
        assertThat(corpus.list(TRANSLATE))
                .extracting(InvocationRecord::input)
                .containsExactly("hello", "goodbye", "thank you");
        assertThat(corpus.list(TRANSLATE))
                .extracting(InvocationRecord::output)
                .containsExactly("hola", "adiós", "gracias");
    }

    /**
     * Step 2: Capture with keys for later key-lookup replay.
     */
    @Test
    void captureWithKeysForDeterministicReplay() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        final var runtime = new SimulationRuntime(captureConfig(TRANSLATE), corpus);

        // Capture with explicit keys derived from input
        runtime.capture(TRANSLATE, "tenant-1", "hello", "hello", "hola");
        runtime.capture(TRANSLATE, "tenant-1", "goodbye", "goodbye", "adiós");

        // Later: switch to key-lookup simulation using the captured corpus
        final var simRuntime = new SimulationRuntime(simConfig(TRANSLATE, "key-lookup"), corpus);
        simRuntime.registerExtractor(TRANSLATE, (String input) -> input);

        final var strategy = simRuntime.<String, String>strategyFor(TRANSLATE).orElseThrow();
        assertThat(strategy.resolve("hello")).isEqualTo("hola");
        assertThat(strategy.resolve("goodbye")).isEqualTo("adiós");
    }

    /**
     * Step 3: Recorded-replay — key-first with sequential fallback.
     *
     * Perfect for replaying captured traffic: known inputs get their
     * recorded responses, unknown inputs get the next available response.
     */
    @Test
    void recordedReplayKeyFirstSequentialFallback() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.seed(TRANSLATE, List.of(
                new InvocationRecord<>("t1", "hello", "hello", "hola", Instant.now()),
                new InvocationRecord<>("t1", "goodbye", "goodbye", "adiós", Instant.now()),
                new InvocationRecord<>("t1", null, "surprise", "¡sorpresa!", Instant.now())));

        final var runtime = new SimulationRuntime(simConfig(TRANSLATE, "recorded-replay"), corpus);
        runtime.registerExtractor(TRANSLATE, (String input) -> input);

        final var strategy = runtime.<String, String>strategyFor(TRANSLATE).orElseThrow();

        // Known keys: deterministic
        assertThat(strategy.resolve("hello")).isEqualTo("hola");
        assertThat(strategy.resolve("goodbye")).isEqualTo("adiós");

        // Unknown key: falls back to sequential (index 0 → "hola")
        assertThat(strategy.resolve("unknown")).isEqualTo("hola");
    }

    /**
     * Tenant isolation: captured data stays scoped to the recording tenant.
     */
    @Test
    void capturedDataIsTenantScoped() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        final var runtime = new SimulationRuntime(captureConfig(TRANSLATE), corpus);

        runtime.capture(TRANSLATE, "hospital-a", "hello", "hola");
        runtime.capture(TRANSLATE, "hospital-b", "hello", "bonjour");

        assertThat(corpus.listByTenant(TRANSLATE, "hospital-a"))
                .hasSize(1)
                .first()
                .satisfies(r -> assertThat(r.output()).isEqualTo("hola"));

        assertThat(corpus.listByTenant(TRANSLATE, "hospital-b"))
                .hasSize(1)
                .first()
                .satisfies(r -> assertThat(r.output()).isEqualTo("bonjour"));
    }

    // --- helpers ---

    static SimulationConfig captureConfig(final String qualifiedName) {
        return new SimulationConfig() {
            @Override
            public Optional<String> strategyFor(final String qn) {
                return Optional.empty();
            }

            @Override
            public boolean captureEnabled(final String qn) {
                return qn.equals(qualifiedName);
            }

            @Override
            public Optional<ExhaustionPolicy> exhaustionPolicy(final String qn) {
                return Optional.empty();
            }
        };
    }

    static SimulationConfig simConfig(final String qualifiedName, final String strategy) {
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
