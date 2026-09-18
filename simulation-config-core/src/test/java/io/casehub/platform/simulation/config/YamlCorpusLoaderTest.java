package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.InvocationRecord;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class YamlCorpusLoaderTest {

    private final YamlCorpusLoader loader = new YamlCorpusLoader();

    @Test
    void loadsEntriesFromYaml() {
        var result = loadTestCorpus("simulation/test-corpus.yaml");

        assertThat(result).containsKeys("my-spi.query", "my-spi.store");
        assertThat(result.get("my-spi.query")).hasSize(2);
        assertThat(result.get("my-spi.store")).hasSize(1);
    }

    @Test
    void parsesKeyAndTenancyId() {
        var result = loadTestCorpus("simulation/test-corpus.yaml");
        InvocationRecord<Object, Object> first = result.get("my-spi.query").get(0);

        assertThat(first.key()).isEqualTo("cardiology");
        assertThat(first.tenancyId()).isEqualTo("hospital-a");
    }

    @Test
    void parsesMapInput() {
        var result = loadTestCorpus("simulation/test-corpus.yaml");
        InvocationRecord<Object, Object> first = result.get("my-spi.query").get(0);

        assertThat(first.input()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) first.input();
        assertThat(input).containsEntry("domain", "cardiology");
    }

    @Test
    void parsesStringOutput() {
        var result = loadTestCorpus("simulation/test-corpus.yaml");
        InvocationRecord<Object, Object> first = result.get("my-spi.query").get(0);

        assertThat(first.output()).isEqualTo("Lab results for cardiology");
    }

    @Test
    void handlesNullKey() {
        var result = loadTestCorpus("simulation/test-corpus.yaml");
        InvocationRecord<Object, Object> storeEntry = result.get("my-spi.store").get(0);

        assertThat(storeEntry.key()).isNull();
    }

    @Test
    void setsRecordedAtToNonNull() {
        var result = loadTestCorpus("simulation/test-corpus.yaml");
        InvocationRecord<Object, Object> first = result.get("my-spi.query").get(0);

        assertThat(first.recordedAt()).isNotNull();
    }

    @Test
    void loadFromPathsMergesAcrossFiles() {
        var result = loader.loadFromPaths(List.of(
                "classpath:simulation/test-corpus.yaml",
                "classpath:simulation/extra-corpus.yaml"));

        assertThat(result.get("my-spi.query")).hasSize(3);
    }

    private Map<String, List<InvocationRecord<Object, Object>>> loadTestCorpus(
            String resource) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(resource);
        assertThat(is).as("Test resource %s", resource).isNotNull();
        return loader.load(is);
    }
}
