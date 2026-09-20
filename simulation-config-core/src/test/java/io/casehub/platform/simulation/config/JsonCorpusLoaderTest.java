package io.casehub.platform.simulation.config;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JsonCorpusLoaderTest {

    private final JsonCorpusLoader loader = new JsonCorpusLoader();

    @Test
    void supportsJsonExtension() {
        assertThat(loader.supports("data.json")).isTrue();
        assertThat(loader.supports("data.JSON")).isTrue();
        assertThat(loader.supports("data.yaml")).isFalse();
    }

    @Test
    void loadsCorpusFromJson() {
        String json = """
                {
                  "test-spi.query": [
                    {"key": "k1", "tenancy-id": "t1", "input": "hello", "output": "world"}
                  ]
                }
                """;
        var result = loader.load(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "default");
        assertThat(result).containsKey("test-spi.query");
        assertThat(result.get("test-spi.query")).hasSize(1);
        assertThat(result.get("test-spi.query").get(0).key()).isEqualTo("k1");
        assertThat(result.get("test-spi.query").get(0).tenancyId()).isEqualTo("t1");
    }

    @Test
    void fallsBackToDefaultTenancyId() {
        String json = """
                {
                  "test-spi.query": [
                    {"key": "k1", "input": "hello", "output": "world"}
                  ]
                }
                """;
        var result = loader.load(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "fallback");
        assertThat(result.get("test-spi.query").get(0).tenancyId()).isEqualTo("fallback");
    }

    @Test
    void emptyJsonReturnsEmptyMap() {
        var result = loader.load(
                new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)), "t1");
        assertThat(result).isEmpty();
    }
}
