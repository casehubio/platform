package io.casehub.yaml.step.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AptPluginSourceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void loadManifestCreatesDefinitionFromStreams() throws IOException {
        String manifest = """
                {
                  "name": "test-action",
                  "actionClass": "io.casehub.yaml.step.catalog.AptPluginSourceTest$TestAction"
                }
                """;
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "name": { "type": "string", "description": "The name" },
                    "count": { "type": "integer" }
                  },
                  "required": ["name"]
                }
                """;

        var source = new AptPluginSource(mapper);
        Definition def = source.loadManifest("test-action",
                new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayInputStream(schema.getBytes(StandardCharsets.UTF_8)));

        assertThat(def.name()).isEqualTo("test-action");
        assertThat(def.inputs()).containsKey("name");
        assertThat(def.inputs()).containsKey("count");
        assertThat(def.inputs().get("name").description()).isEqualTo("The name");
        assertThat(def.action()).isNotNull();
    }

    @Test
    void populateDiscoversP1Plugins() {
        var source = new AptPluginSource(mapper);
        var registry = new CompositePluginRegistry();

        source.populate(registry);

        assertThat(registry.resolve("process")).isPresent();
        assertThat(registry.resolve("rest-call")).isPresent();
        assertThat(registry.resolve("assert")).isPresent();
        assertThat(registry.resolve("process").get().action()).isNotNull();
    }

    @Test
    void populateLoadsSchemaForDiscoveredPlugins() {
        var source = new AptPluginSource(mapper);
        var registry = new CompositePluginRegistry();

        source.populate(registry);

        var processInputs = registry.resolve("process").get().inputs();
        assertThat(processInputs).containsKey("command");
    }

    @Test
    void populateOverridesExistingEntries() {
        var source   = new AptPluginSource(mapper);
        var registry = new CompositePluginRegistry();
        var existing = Definition.of("process")
                                 .description("pre-existing")
                                 .execute((p, s) -> Result.of(Map.of()))
                                 .build();
        registry.register(existing);

        source.populate(registry);

        assertThat(registry.resolve("process").get().description()).isNotEqualTo("pre-existing");
        assertThat(registry.resolve("rest-call")).isPresent();
        assertThat(registry.resolve("assert")).isPresent();
    }

    @Test
    void loadManifest_expressionFormat_mapsToExpressionType() throws IOException {
        String manifest = """
                {
                  "actionClass": "io.casehub.yaml.step.catalog.AptPluginSourceTest$TestAction"
                }
                """;
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "subject": { "type": "string", "format": "expression" },
                    "message": { "type": "string" }
                  }
                }
                """;
        var source = new AptPluginSource(mapper);
        Definition def = source.loadManifest("test",
                new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayInputStream(schema.getBytes(StandardCharsets.UTF_8)));
        assertThat(def.inputs().get("subject").type())
                .isEqualTo(io.casehub.yaml.plugin.api.ParameterType.EXPRESSION);
        assertThat(def.inputs().get("message").type())
                .isEqualTo(io.casehub.yaml.plugin.api.ParameterType.STRING);
    }


    @Test
    void loadManifest_streamingByteArrayTypes_resolvedCorrectly() throws IOException {
        String manifest = """
                          {
                            "actionClass": "io.casehub.yaml.step.catalog.AptPluginSourceTest$TestAction",
                            "streaming": {
                              "inputType": "byte[]",
                              "outputType": "java.lang.String",
                              "inputKey": "audio",
                              "streamingStepClass": "io.casehub.yaml.step.catalog.AptPluginSourceTest$TestStreamingStep"
                            }
                          }
                          """;
        var source = new AptPluginSource(mapper);
        Definition def = source.loadManifest("stt-transcribe",
                                             new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8)),
                                             null);

        assertThat(def.streamingType()).isPresent();
        assertThat(def.streamingType().get().inputType()).isEqualTo(byte[].class);
        assertThat(def.streamingType().get().outputType()).isEqualTo(String.class);
        assertThat(def.streamingType().get().inputKey()).isEqualTo("audio");
    }

    public static class TestAction implements Action {
        @Override
        public Result execute(
                Map<String, Object> parameters,
                io.casehub.yaml.plugin.api.ServiceRegistry services) {
            return Result.of(Map.of());
        }
    }

    public static class TestStreamingStep implements io.casehub.yaml.plugin.api.StreamingStep<byte[], String> {
        @Override
        public Class<byte[]> inputType() {return byte[].class;}

        @Override
        public Class<String> outputType() {return String.class;}

        @Override
        public String apply(byte[] input) {return new String(input);}
    }

}
