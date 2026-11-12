package io.casehub.platform.simulation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.simulation.DataRealism;
import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.MapSimulationConfig;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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

    @Test
    void domainSeedLoadsIntoCorpusAndResolvesAsTypedResponse() {
        String yaml = """
                contacts.lookup:
                  - key: contact-1
                    input:
                      id: contact-1
                    output:
                      id: contact-1
                      name: Ada Lovelace
                      active: true
                      addresses:
                        - city: London
                          postcode: SW1A
                """;
        var loaded = loader.load(stream(yaml), "demo-tenant");
        assertThat(loaded.get("contacts.lookup")).singleElement().satisfies(record -> {
            assertThat(record.tenancyId()).isEqualTo("demo-tenant");
            assertThat(record.input()).isEqualTo(Map.of("id", "contact-1"));
            assertThat(record.recordedAt()).isNotNull();
        });
        var corpus = new InMemorySimulationCorpus<Object, Object>();
        loaded.forEach(corpus::seed);
        var runtime = new SimulationRuntime(
                MapSimulationConfig.builder()
                        .strategy("contacts.lookup", "key-lookup").build(), corpus);
        runtime.registerExtractor("contacts.lookup", input -> ((Map<?, ?>) input).get("id").toString());

        var strategy = runtime.<Object, Object>strategyFor("contacts.lookup").orElseThrow();
        Object output = strategy.resolve(Map.of("id", "contact-1"));
        var contact = new ObjectMapper().convertValue(output, Contact.class);

        assertThat(contact).isEqualTo(new Contact("contact-1", "Ada Lovelace", true,
                List.of(new Address("London", "SW1A"))));
        assertThat(strategy.dataRealism()).isEqualTo(DataRealism.DOMAIN_PLAUSIBLE);
    }

    public record Contact(String id, String name, boolean active, List<Address> addresses) {}
    public record Address(String city, String postcode) {}

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
