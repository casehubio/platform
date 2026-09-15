package io.casehub.platform.simulation.inmem;

import io.casehub.platform.simulation.InvocationRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySimulationCorpusTest {

    private static final String QN = "test-spi.method";
    private static final String QN_OTHER = "other-spi.method";

    // --- record and lookup ---

    @Test
    void recordThenLookupByKeyReturnsOutput() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.record(QN, "t1", "my-key", "input-data", "output-data");

        assertThat(corpus.lookupByKey(QN, "my-key")).contains("output-data");
    }

    @Test
    void lookupByKeyReturnsEmptyForUnknownKey() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.record(QN, "t1", "known", "in", "out");

        assertThat(corpus.lookupByKey(QN, "unknown")).isEmpty();
    }

    @Test
    void lookupByKeyReturnsEmptyForUnknownQualifiedName() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.record(QN, "t1", "key", "in", "out");

        assertThat(corpus.lookupByKey(QN_OTHER, "key")).isEmpty();
    }

    @Test
    void recordWithoutKeyAssignsAutoKey() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.record(QN, "t1", "in", "out");

        assertThat(corpus.size(QN)).isEqualTo(1);
        assertThat(corpus.lookupByIndex(QN, 0)).contains("out");
    }

    // --- lookupByIndex ---

    @Test
    void lookupByIndexReturnsInInsertionOrder() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.record(QN, "t1", "k1", "in1", "first");
        corpus.record(QN, "t1", "k2", "in2", "second");
        corpus.record(QN, "t1", "k3", "in3", "third");

        assertThat(corpus.lookupByIndex(QN, 0)).contains("first");
        assertThat(corpus.lookupByIndex(QN, 1)).contains("second");
        assertThat(corpus.lookupByIndex(QN, 2)).contains("third");
    }

    @Test
    void lookupByIndexReturnsEmptyForOutOfBounds() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.record(QN, "t1", "k1", "in", "out");

        assertThat(corpus.lookupByIndex(QN, 1)).isEmpty();
        assertThat(corpus.lookupByIndex(QN, -1)).isEmpty();
    }

    // --- seed ---

    @Test
    void seedPrePopulatesCorpus() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        final var records = List.of(
                new InvocationRecord<>("t1", "k1", "in1", "out1", Instant.now()),
                new InvocationRecord<>("t1", "k2", "in2", "out2", Instant.now()));
        corpus.seed(QN, records);

        assertThat(corpus.size(QN)).isEqualTo(2);
        assertThat(corpus.lookupByKey(QN, "k1")).contains("out1");
        assertThat(corpus.lookupByKey(QN, "k2")).contains("out2");
    }

    @Test
    void seedAppendsToExistingRecords() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.record(QN, "t1", "existing", "in", "existing-out");
        corpus.seed(QN, List.of(
                new InvocationRecord<>("t1", "seeded", "in", "seeded-out", Instant.now())));

        assertThat(corpus.size(QN)).isEqualTo(2);
    }

    // --- list and listByTenant ---

    @Test
    void listReturnsAllRecordsForQualifiedName() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.record(QN, "t1", "k1", "in1", "out1");
        corpus.record(QN, "t2", "k2", "in2", "out2");
        corpus.record(QN_OTHER, "t1", "k3", "in3", "out3");

        assertThat(corpus.list(QN)).hasSize(2);
        assertThat(corpus.list(QN_OTHER)).hasSize(1);
    }

    @Test
    void listByTenantFiltersByTenancy() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.record(QN, "tenant-a", "k1", "in1", "out1");
        corpus.record(QN, "tenant-b", "k2", "in2", "out2");
        corpus.record(QN, "tenant-a", "k3", "in3", "out3");

        final var tenantA = corpus.listByTenant(QN, "tenant-a");
        assertThat(tenantA).hasSize(2);
        assertThat(tenantA).allSatisfy(r -> assertThat(r.tenancyId()).isEqualTo("tenant-a"));
    }

    // --- clear ---

    @Test
    void clearRemovesOnlyForGivenQualifiedName() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        corpus.record(QN, "t1", "k1", "in1", "out1");
        corpus.record(QN_OTHER, "t1", "k2", "in2", "out2");

        corpus.clear(QN);

        assertThat(corpus.size(QN)).isZero();
        assertThat(corpus.size(QN_OTHER)).isEqualTo(1);
    }

    // --- size ---

    @Test
    void sizeReflectsCurrentCount() {
        final var corpus = new InMemorySimulationCorpus<String, String>();
        assertThat(corpus.size(QN)).isZero();

        corpus.record(QN, "t1", "k1", "in", "out");
        assertThat(corpus.size(QN)).isEqualTo(1);

        corpus.record(QN, "t1", "k2", "in", "out");
        assertThat(corpus.size(QN)).isEqualTo(2);
    }

    // --- thread safety ---

    @Test
    void concurrentRecordDoesNotLoseEntries() throws InterruptedException {
        final var corpus = new InMemorySimulationCorpus<String, Integer>();
        final int threadCount = 8;
        final int recordsPerThread = 100;
        final CountDownLatch latch = new CountDownLatch(threadCount);

        try (final ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    for (int i = 0; i < recordsPerThread; i++) {
                        corpus.record(QN, "t1", "t" + threadId + "-k" + i, "input-" + i, i * 10);
                    }
                    latch.countDown();
                });
            }
            latch.await();
        }

        assertThat(corpus.size(QN)).isEqualTo(threadCount * recordsPerThread);
    }

    // --- empty corpus ---

    @Test
    void emptyCorpusReturnsEmptyForAllOperations() {
        final var corpus = new InMemorySimulationCorpus<String, String>();

        assertThat(corpus.lookupByKey(QN, "any")).isEmpty();
        assertThat(corpus.lookupByIndex(QN, 0)).isEmpty();
        assertThat(corpus.list(QN)).isEmpty();
        assertThat(corpus.listByTenant(QN, "t1")).isEmpty();
        assertThat(corpus.size(QN)).isZero();
    }
}
