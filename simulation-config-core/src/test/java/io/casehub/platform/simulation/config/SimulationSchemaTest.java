package io.casehub.platform.simulation.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationSchemaTest {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    void schemaIsValidJson() throws IOException {
        JsonNode schema = loadSchema();
        assertThat(schema).isNotNull();
        assertThat(schema.has("$schema")).isTrue();
        assertThat(schema.get("$schema").asText())
                .contains("json-schema.org/draft/2020-12");
    }

    @Test
    void schemaDefinesTopLevelProperties() throws IOException {
        JsonNode props = loadSchema().get("properties");
        assertThat(props.has("default-tenancy-id")).isTrue();
        assertThat(props.has("methods")).isTrue();
        assertThat(props.has("profiles")).isTrue();
    }

    @Test
    void schemaDisallowsAdditionalTopLevelProperties() throws IOException {
        JsonNode schema = loadSchema();
        assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
    }

    @Test
    void methodConfigDefinesAllPerMethodKeys() throws IOException {
        JsonNode methodConfig = loadSchema().at("/$defs/method-config/properties");
        assertThat(methodConfig.has("strategy")).isTrue();
        assertThat(methodConfig.has("key-extractor")).isTrue();
        assertThat(methodConfig.has("capture")).isTrue();
        assertThat(methodConfig.has("exhaustion-policy")).isTrue();
        assertThat(methodConfig.has("scorer")).isTrue();
        assertThat(methodConfig.has("threshold")).isTrue();
        assertThat(methodConfig.has("corpus")).isTrue();
        assertThat(methodConfig.has("corpus-files")).isTrue();
    }

    @Test
    void strategyEnumIncludesAllAliases() throws IOException {
        JsonNode strategyEnum = loadSchema()
                .at("/$defs/method-config/properties/strategy/enum");
        assertThat(strategyEnum.isArray()).isTrue();
        assertThat(strategyEnum.size()).isGreaterThanOrEqualTo(10);
        assertThat(strategyEnum.toString()).contains("key-lookup", "key",
                "sequential", "seq", "random", "rand",
                "recorded-replay", "replay", "nearest-match", "nearest");
    }

    @Test
    void corpusEntryRequiresOutput() throws IOException {
        JsonNode corpusEntry = loadSchema().at("/$defs/corpus-entry");
        JsonNode required = corpusEntry.get("required");
        assertThat(required.isArray()).isTrue();
        assertThat(required.get(0).asText()).isEqualTo("output");
    }

    @Test
    void corpusEntryDefinesAllFields() throws IOException {
        JsonNode props = loadSchema().at("/$defs/corpus-entry/properties");
        assertThat(props.has("key")).isTrue();
        assertThat(props.has("tenancy-id")).isTrue();
        assertThat(props.has("input")).isTrue();
        assertThat(props.has("output")).isTrue();
    }

    @Test
    void profileConfigDefinesMethodsAndCorpusFiles() throws IOException {
        JsonNode props = loadSchema().at("/$defs/profile-config/properties");
        assertThat(props.has("methods")).isTrue();
        assertThat(props.has("corpus-files")).isTrue();
    }

    @Test
    void exhaustionPolicyEnumValues() throws IOException {
        JsonNode epEnum = loadSchema()
                .at("/$defs/method-config/properties/exhaustion-policy/enum");
        assertThat(epEnum.isArray()).isTrue();
        assertThat(epEnum.toString()).contains("WRAP", "THROW");
    }

    @Test
    void thresholdHasBounds() throws IOException {
        JsonNode threshold = loadSchema()
                .at("/$defs/method-config/properties/threshold");
        assertThat(threshold.get("minimum").asDouble()).isEqualTo(0.0);
        assertThat(threshold.get("maximum").asDouble()).isEqualTo(1.0);
    }

    private JsonNode loadSchema() throws IOException {
        InputStream is = getClass().getClassLoader()
                .getResourceAsStream("schema/simulation.schema.json");
        assertThat(is).as("simulation.schema.json must be on classpath").isNotNull();
        return JSON_MAPPER.readTree(is);
    }
}
