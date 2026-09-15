package io.casehub.platform.simulation.tutorial;

import io.casehub.platform.simulation.ExhaustionPolicy;
import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.SimulationConfig;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-method SPIs — different strategies for different methods.
 *
 * A real SPI like CaseMemoryStore has query(), store(), erase().
 * Each method can use a different strategy, or no strategy at all.
 */
class PerMethodStrategyTest {

    static final String QUERY = "case-memory-store.query";
    static final String STORE = "case-memory-store.store";
    static final String ERASE = "case-memory-store.erase";

    /**
     * query uses key-lookup (deterministic), store uses sequential,
     * erase has no strategy (passthrough to real impl).
     */
    @Test
    void differentStrategiesPerMethod() {
        final var corpus = new InMemorySimulationCorpus<>();

        // Seed query corpus with keyed responses
        corpus.seed(QUERY, List.of(
                new InvocationRecord<>("t1", "patient-123", "patient-123", "Lab results for patient 123", Instant.now()),
                new InvocationRecord<>("t1", "patient-456", "patient-456", "X-ray for patient 456", Instant.now())));

        // Seed store corpus (sequential — just acknowledges each call)
        corpus.seed(STORE, List.of(
                new InvocationRecord<>("t1", null, "store-input-1", "stored-1", Instant.now()),
                new InvocationRecord<>("t1", null, "store-input-2", "stored-2", Instant.now())));

        // erase: no corpus entries, no strategy — passthrough

        final var config = mapConfig(Map.of(
                QUERY, "key-lookup",
                STORE, "sequential"
                // ERASE deliberately absent
        ));

        final var runtime = new SimulationRuntime(config, corpus);
        runtime.registerExtractor(QUERY, (Object input) -> String.valueOf(input));

        // query: deterministic by key
        assertThat(runtime.strategyFor(QUERY)).isPresent();
        assertThat(runtime.strategyFor(QUERY).get().resolve("patient-123"))
                .isEqualTo("Lab results for patient 123");
        assertThat(runtime.strategyFor(QUERY).get().resolve("patient-456"))
                .isEqualTo("X-ray for patient 456");

        // store: sequential
        assertThat(runtime.strategyFor(STORE)).isPresent();
        assertThat(runtime.strategyFor(STORE).get().resolve("anything"))
                .isEqualTo("stored-1");
        assertThat(runtime.strategyFor(STORE).get().resolve("anything"))
                .isEqualTo("stored-2");

        // erase: passthrough — no strategy configured
        assertThat(runtime.strategyFor(ERASE)).isEmpty();
    }

    // --- helper: per-method config from a Map ---

    static SimulationConfig mapConfig(final Map<String, String> strategies) {
        return new SimulationConfig() {
            @Override
            public Optional<String> strategyFor(final String qn) {
                return Optional.ofNullable(strategies.get(qn));
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
