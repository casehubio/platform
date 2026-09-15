package io.casehub.platform.simulation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SimulationApiTest {

    // --- InvocationRecord ---

    @Test
    void recordPreservesAllFields() {
        final Instant now = Instant.now();
        final var record = new InvocationRecord<>("tenant-1", "k1", "input-data", "output-data", now);

        assertThat(record.tenancyId()).isEqualTo("tenant-1");
        assertThat(record.key()).isEqualTo("k1");
        assertThat(record.input()).isEqualTo("input-data");
        assertThat(record.output()).isEqualTo("output-data");
        assertThat(record.recordedAt()).isEqualTo(now);
    }

    @Test
    void recordSupportsNullKey() {
        final var record = new InvocationRecord<>("t1", null, "in", "out", Instant.now());
        assertThat(record.key()).isNull();
    }

    @Test
    void recordEquality() {
        final Instant ts = Instant.parse("2026-01-01T00:00:00Z");
        final var a = new InvocationRecord<>("t1", "k", 42, true, ts);
        final var b = new InvocationRecord<>("t1", "k", 42, true, ts);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    // --- NoOpSimulationCorpus ---

    @Test
    void noOpCorpusLookupByKeyReturnsEmpty() {
        final var corpus = new NoOpSimulationCorpus<String, String>();
        assertThat(corpus.lookupByKey("spi.method", "any-key")).isEmpty();
    }

    @Test
    void noOpCorpusLookupByIndexReturnsEmpty() {
        final var corpus = new NoOpSimulationCorpus<String, String>();
        assertThat(corpus.lookupByIndex("spi.method", 0)).isEmpty();
    }

    @Test
    void noOpCorpusListReturnsEmptyList() {
        final var corpus = new NoOpSimulationCorpus<String, String>();
        assertThat(corpus.list("spi.method")).isEmpty();
    }

    @Test
    void noOpCorpusListByTenantReturnsEmptyList() {
        final var corpus = new NoOpSimulationCorpus<String, String>();
        assertThat(corpus.listByTenant("spi.method", "tenant-1")).isEmpty();
    }

    @Test
    void noOpCorpusSizeReturnsZero() {
        final var corpus = new NoOpSimulationCorpus<String, String>();
        assertThat(corpus.size("spi.method")).isZero();
    }

    @Test
    void noOpCorpusRecordDoesNotThrow() {
        final var corpus = new NoOpSimulationCorpus<String, String>();
        assertThatCode(() -> corpus.record("spi.method", "t1", "in", "out"))
                .doesNotThrowAnyException();
    }

    @Test
    void noOpCorpusRecordWithKeyDoesNotThrow() {
        final var corpus = new NoOpSimulationCorpus<String, String>();
        assertThatCode(() -> corpus.record("spi.method", "t1", "key", "in", "out"))
                .doesNotThrowAnyException();
    }

    @Test
    void noOpCorpusSeedDoesNotThrow() {
        final var corpus = new NoOpSimulationCorpus<String, String>();
        final var records = List.of(
                new InvocationRecord<>("t1", "k1", "in", "out", Instant.now()));
        assertThatCode(() -> corpus.seed("spi.method", records))
                .doesNotThrowAnyException();
    }

    @Test
    void noOpCorpusClearDoesNotThrow() {
        final var corpus = new NoOpSimulationCorpus<String, String>();
        assertThatCode(() -> corpus.clear("spi.method"))
                .doesNotThrowAnyException();
    }

    @Test
    void noOpCorpusRemainsEmptyAfterRecordAndSeed() {
        final var corpus = new NoOpSimulationCorpus<String, String>();
        corpus.record("spi.method", "t1", "in", "out");
        corpus.seed("spi.method", List.of(
                new InvocationRecord<>("t1", "k1", "in", "out", Instant.now())));

        assertThat(corpus.size("spi.method")).isZero();
        assertThat(corpus.list("spi.method")).isEmpty();
        assertThat(corpus.lookupByKey("spi.method", "k1")).isEmpty();
    }

    // --- Enums ---

    @Test
    void dataRealismHasExpectedValues() {
        assertThat(DataRealism.values()).containsExactly(
                DataRealism.GARBAGE,
                DataRealism.PLACEHOLDER,
                DataRealism.STRUCTURALLY_VALID,
                DataRealism.DOMAIN_PLAUSIBLE,
                DataRealism.RECORDED_REAL);
    }

    @Test
    void exhaustionPolicyHasExpectedValues() {
        assertThat(ExhaustionPolicy.values()).containsExactly(
                ExhaustionPolicy.WRAP,
                ExhaustionPolicy.THROW);
    }

    // --- Exceptions ---

    @Test
    void simulationExhaustedExceptionPreservesMessage() {
        final var ex = new SimulationExhaustedException("corpus empty at index 5");
        assertThat(ex).hasMessage("corpus empty at index 5");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void simulationKeyNotFoundExceptionPreservesKey() {
        final var ex = new SimulationKeyNotFoundException("missing-key");
        assertThat(ex.getKey()).isEqualTo("missing-key");
        assertThat(ex).hasMessageContaining("missing-key");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void simulationConfigExceptionPreservesMessage() {
        final var ex = new SimulationConfigException("bad strategy name");
        assertThat(ex).hasMessage("bad strategy name");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    // --- @SimulationEligible ---

    @Test
    void simulationEligibleIsRetainedAtRuntime() {
        assertThat(SimulationEligible.class.getAnnotation(
                java.lang.annotation.Retention.class).value())
                .isEqualTo(java.lang.annotation.RetentionPolicy.RUNTIME);
    }

    @Test
    void simulationEligibleTargetsTypes() {
        assertThat(SimulationEligible.class.getAnnotation(
                java.lang.annotation.Target.class).value())
                .containsExactly(java.lang.annotation.ElementType.TYPE);
    }

    // --- SimulationStrategy / SimulationCorpus / KeyExtractor contract shapes ---

    @Test
    void strategyContractShape() {
        SimulationStrategy<String, Integer> strategy = new SimulationStrategy<>() {
            @Override
            public Integer resolve(final String input) {
                return input.length();
            }

            @Override
            public boolean canResolve(final String input) {
                return input != null;
            }
        };

        assertThat(strategy.canResolve("hello")).isTrue();
        assertThat(strategy.resolve("hello")).isEqualTo(5);
    }

    @Test
    void keyExtractorIsFunctionalInterface() {
        KeyExtractor<String> extractor = String::toUpperCase;
        assertThat(extractor.extract("hello")).isEqualTo("HELLO");
    }
}
