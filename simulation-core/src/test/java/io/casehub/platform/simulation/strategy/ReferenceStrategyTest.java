package io.casehub.platform.simulation.strategy;

import io.casehub.platform.simulation.DataRealism;
import io.casehub.platform.simulation.Seedable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.casehub.platform.simulation.strategy.ListBackedCorpus.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferenceStrategyTest {

    private static final String QN = "voice-platform.synthesise";

    @Test
    void canResolveAlwaysReturnsFalse() {
        var corpus = ListBackedCorpus.<String, Map<String, Object>>of(
                entry("k1", "hello", Map.of("audio", "data")));

        var strategy = new ReferenceStrategy<>(corpus, QN, null);

        assertThat(strategy.canResolve("any")).isFalse();
    }

    @Test
    void resolveThrowsBecauseDelegateHandlesExecution() {
        var corpus = ListBackedCorpus.<String, Map<String, Object>>of(
                entry("k1", "hello", Map.of("audio", "data")));

        var strategy = new ReferenceStrategy<>(corpus, QN, null);

        assertThatThrownBy(() -> strategy.resolve("any"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void seedsWhenSeedableProvided() {
        var corpus = ListBackedCorpus.<String, Map<String, Object>>of(
                entry("k1", "hello", Map.of("text", "Hello", "language", "en")),
                entry("k2", "world", Map.of("text", "World", "language", "fr")));

        var recorder = new RecordingSeedable();
        var strategy = new ReferenceStrategy<>(corpus, QN, recorder);

        assertThat(strategy.isSeeded()).isTrue();
        assertThat(recorder.seededMethodName).isEqualTo(QN);
        assertThat(recorder.seededOutputs).hasSize(2);
        assertThat(recorder.seededOutputs.get(0)).containsEntry("text", "Hello");
        assertThat(recorder.seededOutputs.get(1)).containsEntry("language", "fr");
    }

    @Test
    void doesNotSeedWhenCorpusEmpty() {
        var corpus = ListBackedCorpus.<String, Map<String, Object>>of();

        var recorder = new RecordingSeedable();
        var strategy = new ReferenceStrategy<>(corpus, QN, recorder);

        assertThat(strategy.isSeeded()).isFalse();
        assertThat(recorder.seededMethodName).isNull();
    }

    @Test
    void dataRealismIsRecordedReal() {
        var corpus = ListBackedCorpus.<String, Map<String, Object>>of();
        var strategy = new ReferenceStrategy<>(corpus, QN, null);

        assertThat(strategy.dataRealism()).isEqualTo(DataRealism.RECORDED_REAL);
    }

    static class RecordingSeedable implements Seedable {
        String seededMethodName;
        List<Map<String, Object>> seededOutputs;

        @Override
        public void seed(String qualifiedMethodName, List<Map<String, Object>> corpusOutputs) {
            this.seededMethodName = qualifiedMethodName;
            this.seededOutputs = new ArrayList<>(corpusOutputs);
        }
    }
}
