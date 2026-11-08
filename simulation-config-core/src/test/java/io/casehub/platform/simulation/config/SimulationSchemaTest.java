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
        assertThat(methodConfig.has("fallthrough-realism")).isTrue();
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

    @Test
    void methodConfigDefinesFallthroughRealism() throws IOException {
        JsonNode fr = loadSchema()
                              .at("/$defs/method-config/properties/fallthrough-realism");
        assertThat(fr.has("enum")).isTrue();
        assertThat(fr.get("enum").toString()).contains(
                "STRUCTURALLY_VALID", "DOMAIN_PLAUSIBLE", "RECORDED_REAL");
    }


    @Test
    void schemaHasTemporalProfilesProperty() throws IOException {
        JsonNode props = loadSchema().get("properties");
        assertThat(props.has("temporal-profiles")).isTrue();
        assertThat(props.at("/temporal-profiles/additionalProperties/$ref").asText())
                .isEqualTo("#/$defs/temporal-profile-config");
    }

    @Test
    void temporalProfileConfigDefined() throws IOException {
        JsonNode config = loadSchema().at("/$defs/temporal-profile-config");
        JsonNode props  = config.get("properties");
        assertThat(props.has("qualified-name")).isTrue();
        assertThat(props.has("tenancy-id")).isTrue();
        assertThat(props.has("loop")).isTrue();
        assertThat(props.has("speed")).isTrue();
        assertThat(props.has("events")).isTrue();
        assertThat(props.has("events-file")).isTrue();
        assertThat(props.has("from-corpus")).isTrue();
        assertThat(props.has("sequence")).isTrue();
        assertThat(config.get("oneOf")).hasSize(4);
    }

    @Test
    void temporalEventDefined() throws IOException {
        JsonNode event = loadSchema().at("/$defs/temporal-event");
        assertThat(event.get("required").toString()).contains("payload");
        JsonNode props = event.get("properties");
        assertThat(props.has("delay")).isTrue();
        assertThat(props.has("label")).isTrue();
        assertThat(props.has("payload")).isTrue();
    }

    @Test
    void sequenceRefDefined() throws IOException {
        JsonNode ref = loadSchema().at("/$defs/sequence-ref");
        assertThat(ref.get("required").toString()).contains("ref");
        assertThat(ref.get("properties").has("ref")).isTrue();
        assertThat(ref.get("properties").has("delay")).isTrue();
    }

    @Test
    void profileConfigHasTemporalProperty() throws IOException {
        JsonNode props = loadSchema().at("/$defs/profile-config/properties");
        assertThat(props.has("temporal")).isTrue();
        assertThat(props.at("/temporal/type").asText()).isEqualTo("array");
    }

    @Test
    void methodConfigDefinesCorpusSelection() throws IOException {
        JsonNode props = loadSchema().at("/$defs/method-config/properties");
        assertThat(props.has("corpus-selection")).isTrue();
    }

    @Test
    void corpusSelectionConfigDefined() throws IOException {
        JsonNode config = loadSchema().at("/$defs/corpus-selection-config");
        assertThat(config.has("required")).isTrue();
        assertThat(config.get("required").toString()).contains("type");
        JsonNode typeEnum = config.at("/properties/type/enum");
        assertThat(typeEnum.toString()).contains("state-machine", "duration", "lookup-count", "random");
    }

    @Test
    void corpusAndCorpusSelectionMutuallyExclusiveInSchema() throws IOException {
        JsonNode methodConfig = loadSchema().at("/$defs/method-config");
        assertThat(methodConfig.has("not")).isTrue();
    }

    @Test
    void corpusSelectionPhaseDefined() throws IOException {
        JsonNode phase = loadSchema().at("/$defs/corpus-selection-phase");
        assertThat(phase.get("required").toString()).contains("name");
        JsonNode props = phase.get("properties");
        assertThat(props.has("name")).isTrue();
        assertThat(props.has("duration")).isTrue();
        assertThat(props.has("count")).isTrue();
        assertThat(props.has("weight")).isTrue();
        assertThat(props.has("corpus")).isTrue();
        assertThat(props.has("corpus-files")).isTrue();
    }


    @Test
    void triggerConfigDefined() throws IOException {
        JsonNode trigger = loadSchema().at("/$defs/trigger-config");
        assertThat(trigger.get("required").toString()).contains("event-type");
        JsonNode props = trigger.get("properties");
        assertThat(props.has("event-type")).isTrue();
        assertThat(props.has("condition")).isTrue();
    }

    @Test
    void topLevelTriggersProperty() throws IOException {
        JsonNode props = loadSchema().at("/properties");
        assertThat(props.has("triggers")).isTrue();
    }

    private JsonNode loadSchema() throws IOException {
        InputStream is = getClass().getClassLoader()
                .getResourceAsStream("schema/simulation.schema.json");
        assertThat(is).as("simulation.schema.json must be on classpath").isNotNull();
        return JSON_MAPPER.readTree(is);
    }
}
