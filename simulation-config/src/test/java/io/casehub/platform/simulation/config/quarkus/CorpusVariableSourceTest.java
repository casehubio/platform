package io.casehub.platform.simulation.config.quarkus;

import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorpusVariableSourceTest {

    private InMemorySimulationCorpus<java.util.Map<String, Object>, Object> corpus;
    private CorpusVariableSource                                            source;

    @BeforeEach
    void setUp() {
        corpus = new InMemorySimulationCorpus<>();
        corpus.seed("trades", java.util.List.of(
                new InvocationRecord<>("t1", "k1", java.util.Map.of("symbol", "AAPL", "price", 150.0), null, java.time.Instant.now()),
                new InvocationRecord<>("t1", "k2", java.util.Map.of("symbol", "TSLA", "price", 201.0), null, java.time.Instant.now())
                                               ));
        source = new CorpusVariableSource(corpus);
    }

    @Test
    void resolve_qualifiedName_returnsList() {
        Object result = source.resolve("trades");
        assertThat(result).isInstanceOf(java.util.List.class);
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> items = (java.util.List<java.util.Map<String, Object>>) result;
        assertThat(items).hasSize(2);
        assertThat(items.get(0)).containsEntry("symbol", "AAPL");
    }

    @Test
    void resolve_indexedAccess_returnsElement() {
        Object result = source.resolve("trades[0]");
        assertThat(result).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> item = (java.util.Map<String, Object>) result;
        assertThat(item).containsEntry("symbol", "AAPL");
    }

    @Test
    void resolve_indexedAccess_secondElement() {
        Object result = source.resolve("trades[1]");
        assertThat(result).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> item = (java.util.Map<String, Object>) result;
        assertThat(item).containsEntry("symbol", "TSLA");
    }

    @Test
    void resolve_unknownQualifiedName_returnsNull() {
        Object result = source.resolve("nonexistent");
        assertThat(result).isNull();
    }

    @Test
    void resolve_outOfBoundsIndex_returnsNull() {
        Object result = source.resolve("trades[99]");
        assertThat(result).isNull();
    }
}
