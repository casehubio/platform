package io.casehub.platform.simulation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhasedCorpusTest {

    private static final String QN = "test-spi.query";

    @Test
    void lookupByKeyDelegatesToActivePhase() {
        var phase = new AtomicReference<>("phase-a");
        CorpusSelector selector = phase::get;
        var phases = Map.<String, List<InvocationRecord<Object, Object>>>of(
                "phase-a", List.of(InvocationRecord.of("t1", "k1", "in-a", "out-a")),
                "phase-b", List.of(InvocationRecord.of("t1", "k1", "in-b", "out-b")));
        var corpus = new PhasedCorpus<>(selector, phases);

        assertThat(corpus.lookupByKey(QN, "k1")).contains("out-a");

        phase.set("phase-b");
        assertThat(corpus.lookupByKey(QN, "k1")).contains("out-b");
    }

    @Test
    void lookupAllByKeyDelegatesToActivePhase() {
        var phase = new AtomicReference<>("a");
        CorpusSelector selector = phase::get;
        var phases = Map.<String, List<InvocationRecord<Object, Object>>>of(
                "a", List.of(
                        InvocationRecord.of("t1", "k1", "in1", "out1"),
                        InvocationRecord.of("t1", "k1", "in2", "out2")),
                "b", List.of(InvocationRecord.of("t1", "k1", "in3", "out3")));
        var corpus = new PhasedCorpus<>(selector, phases);

        assertThat(corpus.lookupAllByKey(QN, "k1")).containsExactly("out1", "out2");

        phase.set("b");
        assertThat(corpus.lookupAllByKey(QN, "k1")).containsExactly("out3");
    }

    @Test
    void lookupByIndexDelegatesToActivePhase() {
        var phase = new AtomicReference<>("first");
        CorpusSelector selector = phase::get;
        var phases = Map.<String, List<InvocationRecord<Object, Object>>>of(
                "first", List.of(InvocationRecord.of("t1", null, "in1", "out1")),
                "second", List.of(InvocationRecord.of("t1", null, "in2", "out2")));
        var corpus = new PhasedCorpus<>(selector, phases);

        assertThat(corpus.lookupByIndex(QN, 0)).contains("out1");

        phase.set("second");
        assertThat(corpus.lookupByIndex(QN, 0)).contains("out2");
    }

    @Test
    void lookupByIndexReturnsEmptyForOutOfBounds() {
        CorpusSelector selector = () -> "a";
        var corpus = new PhasedCorpus<>(selector,
                Map.of("a", List.of(InvocationRecord.of("t1", null, "in", "out"))));

        assertThat(corpus.lookupByIndex(QN, 5)).isEmpty();
        assertThat(corpus.lookupByIndex(QN, -1)).isEmpty();
    }

    @Test
    void listReturnsActivePhaseEntries() {
        CorpusSelector selector = () -> "only";
        var entry = InvocationRecord.of("t1", "k", "in", "out");
        var corpus = new PhasedCorpus<>(selector, Map.of("only", List.of(entry)));

        assertThat(corpus.list(QN)).hasSize(1);
        assertThat(corpus.list(QN).get(0).output()).isEqualTo("out");
    }

    @Test
    void sizeReturnsActivePhaseSize() {
        CorpusSelector selector = () -> "a";
        var corpus = new PhasedCorpus<>(selector, Map.of(
                "a", List.of(InvocationRecord.of("t1", null, "in", "out")),
                "b", List.of()));

        assertThat(corpus.size(QN)).isEqualTo(1);
    }

    @Test
    void unknownPhaseThrowsSimulationConfigException() {
        CorpusSelector selector = () -> "nonexistent";
        var corpus = new PhasedCorpus<>(selector, Map.of("a", List.of()));

        assertThatThrownBy(() -> corpus.lookupByKey(QN, "any"))
                .isInstanceOf(SimulationConfigException.class)
                .hasMessageContaining("nonexistent")
                .hasMessageContaining("a");
    }

    @Test
    void mutationMethodsThrowUnsupportedOperationException() {
        CorpusSelector selector = () -> "a";
        var corpus = new PhasedCorpus<>(selector, Map.of("a", List.of()));

        assertThatThrownBy(() -> corpus.record(QN, "t1", "in", "out"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> corpus.record(QN, "t1", "k", "in", "out"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> corpus.seed(QN, List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> corpus.clear(QN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void listByTenantFiltersActivePhase() {
        CorpusSelector selector = () -> "a";
        var corpus = new PhasedCorpus<>(selector, Map.of("a", List.of(
                InvocationRecord.of("tenant-1", "k", "in1", "out1"),
                InvocationRecord.of("tenant-2", "k", "in2", "out2"))));

        assertThat(corpus.listByTenant(QN, "tenant-1")).hasSize(1);
        assertThat(corpus.listByTenant(QN, "tenant-1").get(0).output()).isEqualTo("out1");
    }
}
