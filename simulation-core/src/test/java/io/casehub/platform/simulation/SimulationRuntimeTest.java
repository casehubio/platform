package io.casehub.platform.simulation;

import io.casehub.platform.simulation.strategy.KeyLookupStrategy;
import io.casehub.platform.simulation.strategy.RandomStrategy;
import io.casehub.platform.simulation.strategy.RecordedReplayStrategy;
import io.casehub.platform.simulation.strategy.SequentialStrategy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationRuntimeTest {

    private static final String QN = "test-spi.query";

    // --- strategyFor ---

    @Test
    void strategyForReturnsEmptyWhenNoConfig() {
        final var config = stubConfig(Optional.empty(), false, Optional.empty());
        final var runtime = new SimulationRuntime(config, new NoOpSimulationCorpus<>());

        assertThat(runtime.<String, String>strategyFor(QN)).isEmpty();
    }

    @Test
    void strategyForReturnsSequentialStrategy() {
        final var config = stubConfig(Optional.of("sequential"), false, Optional.empty());
        final var corpus = new NoOpSimulationCorpus<>();
        final var runtime = new SimulationRuntime(config, corpus);

        final var strategy = runtime.strategyFor(QN);
        assertThat(strategy).isPresent();
        assertThat(strategy.get()).isInstanceOf(SequentialStrategy.class);
    }

    @Test
    void strategyForReturnsRandomStrategy() {
        final var config = stubConfig(Optional.of("random"), false, Optional.empty());
        final var corpus = new NoOpSimulationCorpus<>();
        final var runtime = new SimulationRuntime(config, corpus);

        final var strategy = runtime.strategyFor(QN);
        assertThat(strategy).isPresent();
        assertThat(strategy.get()).isInstanceOf(RandomStrategy.class);
    }

    @Test
    void strategyForReturnsKeyLookupWhenExtractorRegistered() {
        final var config = stubConfig(Optional.of("key-lookup"), false, Optional.empty());
        final var corpus = new NoOpSimulationCorpus<>();
        final var runtime = new SimulationRuntime(config, corpus);
        runtime.registerExtractor(QN, (String input) -> input.toUpperCase());

        final var strategy = runtime.strategyFor(QN);
        assertThat(strategy).isPresent();
        assertThat(strategy.get()).isInstanceOf(KeyLookupStrategy.class);
    }

    @Test
    void strategyForThrowsWhenKeyLookupWithoutExtractor() {
        final var config = stubConfig(Optional.of("key-lookup"), false, Optional.empty());
        final var runtime = new SimulationRuntime(config, new NoOpSimulationCorpus<>());

        assertThatThrownBy(() -> runtime.strategyFor(QN))
                .isInstanceOf(SimulationConfigException.class)
                .hasMessageContaining("KeyExtractor");
    }

    @Test
    void strategyForReturnsRecordedReplayWhenExtractorRegistered() {
        final var config = stubConfig(Optional.of("recorded-replay"), false, Optional.empty());
        final var corpus = new NoOpSimulationCorpus<>();
        final var runtime = new SimulationRuntime(config, corpus);
        runtime.registerExtractor(QN, (String input) -> input);

        final var strategy = runtime.strategyFor(QN);
        assertThat(strategy).isPresent();
        assertThat(strategy.get()).isInstanceOf(RecordedReplayStrategy.class);
    }

    @Test
    void strategyForThrowsOnUnknownStrategyName() {
        final var config = stubConfig(Optional.of("nonexistent"), false, Optional.empty());
        final var runtime = new SimulationRuntime(config, new NoOpSimulationCorpus<>());

        assertThatThrownBy(() -> runtime.strategyFor(QN))
                .isInstanceOf(SimulationConfigException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void strategyIsCachedAcrossCalls() {
        final var config = stubConfig(Optional.of("sequential"), false, Optional.empty());
        final var runtime = new SimulationRuntime(config, new NoOpSimulationCorpus<>());

        final var first = runtime.strategyFor(QN);
        final var second = runtime.strategyFor(QN);
        assertThat(first.get()).isSameAs(second.get());
    }

    // --- captureEnabled ---

    @Test
    void captureEnabledDelegatesToConfig() {
        final var config = stubConfig(Optional.empty(), true, Optional.empty());
        final var runtime = new SimulationRuntime(config, new NoOpSimulationCorpus<>());

        assertThat(runtime.captureEnabled(QN)).isTrue();
    }

    @Test
    void captureDisabledWhenConfigSaysNo() {
        final var config = stubConfig(Optional.empty(), false, Optional.empty());
        final var runtime = new SimulationRuntime(config, new NoOpSimulationCorpus<>());

        assertThat(runtime.captureEnabled(QN)).isFalse();
    }

    // --- capture ---

    @Test
    void captureDelegatesToCorpus() {
        final var config = stubConfig(Optional.empty(), true, Optional.empty());
        final var corpus = new TestCorpus();
        final var runtime = new SimulationRuntime(config, corpus);

        runtime.capture(QN, "tenant-1", "input-val", "output-val");

        assertThat(corpus.lastQualifiedName).isEqualTo(QN);
        assertThat(corpus.lastTenancyId).isEqualTo("tenant-1");
    }

    @Test
    void captureWithKeyDelegatesToCorpus() {
        final var config = stubConfig(Optional.empty(), true, Optional.empty());
        final var corpus = new TestCorpus();
        final var runtime = new SimulationRuntime(config, corpus);

        runtime.capture(QN, "tenant-1", "my-key", "input-val", "output-val");

        assertThat(corpus.lastKey).isEqualTo("my-key");
    }

    // --- exhaustionPolicy ---

    @Test
    void sequentialStrategyUsesConfiguredExhaustionPolicy() {
        final var config = stubConfig(Optional.of("sequential"), false, Optional.of(ExhaustionPolicy.THROW));
        final var corpus = new TestCorpus();
        corpus.seed(QN, List.of(new InvocationRecord<>("t1", "k", "in", "out", Instant.now())));
        final var runtime = new SimulationRuntime(config, corpus);

        final var strategy = runtime.strategyFor(QN);
        assertThat(strategy).isPresent();
        strategy.get().resolve("any");

        assertThatThrownBy(() -> strategy.get().resolve("any"))
                .isInstanceOf(SimulationExhaustedException.class);
    }

    // --- helpers ---

    private static SimulationConfig stubConfig(final Optional<String> strategy,
                                               final boolean capture,
                                               final Optional<ExhaustionPolicy> exhaustion) {
        return new SimulationConfig() {
            @Override
            public Optional<String> strategyFor(final String qualifiedName) {
                return strategy;
            }

            @Override
            public boolean captureEnabled(final String qualifiedName) {
                return capture;
            }

            @Override
            public Optional<ExhaustionPolicy> exhaustionPolicy(final String qualifiedName) {
                return exhaustion;
            }
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static class TestCorpus implements SimulationCorpus {

        String lastQualifiedName;
        String lastTenancyId;
        String lastKey;
        private final java.util.List records = new java.util.ArrayList();

        @Override
        public Optional lookupByKey(final String qualifiedName, final String key) {
            return records.stream()
                    .filter(r -> key.equals(((InvocationRecord) r).key()))
                    .map(r -> ((InvocationRecord) r).output())
                    .findFirst();
        }

        @Override
        public Optional lookupByIndex(final String qualifiedName, final int index) {
            if (index < 0 || index >= records.size()) return Optional.empty();
            return Optional.of(((InvocationRecord) records.get(index)).output());
        }

        @Override
        public java.util.List list(final String qualifiedName) {
            return records;
        }

        @Override
        public java.util.List listByTenant(final String qualifiedName, final String tenancyId) {
            return records;
        }

        @Override
        public void record(final String qualifiedName, final String tenancyId, final Object input, final Object output) {
            lastQualifiedName = qualifiedName;
            lastTenancyId = tenancyId;
            records.add(new InvocationRecord<>(tenancyId, null, input, output, Instant.now()));
        }

        @Override
        public void record(final String qualifiedName, final String tenancyId, final String key, final Object input, final Object output) {
            lastQualifiedName = qualifiedName;
            lastTenancyId = tenancyId;
            lastKey = key;
            records.add(new InvocationRecord<>(tenancyId, key, input, output, Instant.now()));
        }

        @Override
        public void seed(final String qualifiedName, final java.util.List newRecords) {
            records.addAll(newRecords);
        }

        @Override
        public void clear(final String qualifiedName) {
            records.clear();
        }

        @Override
        public int size(final String qualifiedName) {
            return records.size();
        }
    }
}
