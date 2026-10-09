package io.casehub.yaml.step.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StepSchemaComposerExtTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode composeSchema() {
        var registry = new CompositePluginRegistry();
        return StepSchemaComposer.compose(registry, mapper);
    }

    @Test
    void schemaContainsNewDecoratorKeys() {
        ObjectNode schema = composeSchema();
        ObjectNode props = (ObjectNode) schema.get("properties");
        assertThat(props.has("at")).isTrue();
        assertThat(props.has("on-complete")).isTrue();
        assertThat(props.has("resource")).isTrue();
        assertThat(props.has("priority")).isTrue();
    }

    @Test
    void atSchemaHasOneOf() {
        ObjectNode schema = composeSchema();
        ObjectNode atSchema = (ObjectNode) schema.get("properties").get("at");
        assertThat(atSchema.has("oneOf")).isTrue();
        assertThat(atSchema.get("oneOf").size()).isEqualTo(3);
    }

    @Test
    void prioritySchemaHasEnum() {
        ObjectNode schema = composeSchema();
        ObjectNode prioritySchema = (ObjectNode) schema.get("properties").get("priority");
        assertThat(prioritySchema.has("enum")).isTrue();
        assertThat(prioritySchema.get("enum").size()).isEqualTo(3);
    }

    @Test
    void onCompleteSchemaHasRecursiveRef() {
        ObjectNode schema = composeSchema();
        ObjectNode onComplete = (ObjectNode) schema.get("properties").get("on-complete");
        assertThat(onComplete.get("type").asText()).isEqualTo("array");
        assertThat(onComplete.get("items").get("$ref").asText()).isEqualTo("#");
    }

    @Test
    void resourceSchemaIsString() {
        ObjectNode schema = composeSchema();
        ObjectNode resource = (ObjectNode) schema.get("properties").get("resource");
        assertThat(resource.get("type").asText()).isEqualTo("string");
    }
}
