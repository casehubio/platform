package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.InvocationRecord;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class YamlCorpusLoaderTest {

    private final YamlCorpusLoader loader = new YamlCorpusLoader();

    @Test
    void supportsYamlExtensions() {
        assertThat(loader.supports("data.yaml")).isTrue();
        assertThat(loader.supports("data.yml")).isTrue();
        assertThat(loader.supports("data.YAML")).isTrue();
        assertThat(loader.supports("data.csv")).isFalse();
        assertThat(loader.supports("data.json")).isFalse();
    }

    @Test
    void loadsCorpusFromYaml() {
        String yaml = """
                test-spi.query:
                  - key: cardiology
                    tenancy-id: hospital-a
                    input:
                      domain: cardiology
                    output: "Lab results"
                  - key: neurology
                    input:
                      domain: neurology
                    output: "MRI results"
                """;
        var result = loader.load(stream(yaml), "default-tenant");

        assertThat(result).containsKey("test-spi.query");
        List<InvocationRecord<Object, Object>> records = result.get("test-spi.query");
        assertThat(records).hasSize(2);
        assertThat(records.get(0).tenancyId()).isEqualTo("hospital-a");
        assertThat(records.get(0).key()).isEqualTo("cardiology");
        assertThat(records.get(1).tenancyId()).isEqualTo("default-tenant");
    }

    @Test
    void emptyYamlReturnsEmptyMap() {
        var result = loader.load(stream(""), "t1");
        assertThat(result).isEmpty();
    }

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
