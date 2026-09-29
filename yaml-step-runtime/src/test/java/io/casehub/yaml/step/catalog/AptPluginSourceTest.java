package io.casehub.yaml.step.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.CatalogEntry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AptPluginSourceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void priorityIs200() {
        var source = new AptPluginSource(mapper);
        assertThat(source.priority()).isEqualTo(200);
    }

    @Test
    void loadManifestCreatesEntryFromStreams() throws IOException {
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
        CatalogEntry entry = source.loadManifest("test-action",
                new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayInputStream(schema.getBytes(StandardCharsets.UTF_8)));

        assertThat(entry.qualifiedName()).isEqualTo("test-action");
        assertThat(entry.definition().name()).isEqualTo("test-action");
        assertThat(entry.definition().inputs()).containsKey("name");
        assertThat(entry.definition().inputs()).containsKey("count");
        assertThat(entry.definition().inputs().get("name").description()).isEqualTo("The name");
        assertThat(entry.action()).isNotNull();
    }

    @Test
    void populateDiscoversP1Plugins() {
        var source = new AptPluginSource(mapper);
        Map<String, CatalogEntry> entries = new LinkedHashMap<>();

        source.populate(entries);

        assertThat(entries).containsKey("process");
        assertThat(entries).containsKey("rest-call");
        assertThat(entries).containsKey("assert");
        assertThat(entries.get("process").action()).isNotNull();
    }

    @Test
    void populateLoadsSchemaForDiscoveredPlugins() {
        var source = new AptPluginSource(mapper);
        Map<String, CatalogEntry> entries = new LinkedHashMap<>();

        source.populate(entries);

        var processInputs = entries.get("process").definition().inputs();
        assertThat(processInputs).containsKey("command");
    }

    @Test
    void populateDoesNotOverwriteExistingEntries() {
        var source = new AptPluginSource(mapper);
        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        var existingEntry = new CatalogEntry("process", null, null);
        entries.put("process", existingEntry);

        source.populate(entries);

        assertThat(entries.get("process")).isSameAs(existingEntry);
        assertThat(entries).containsKey("rest-call");
        assertThat(entries).containsKey("assert");
    }

    public static class TestAction implements Action {
        @Override
        public Result execute(
                Map<String, Object> parameters,
                io.casehub.yaml.plugin.api.ServiceRegistry services) {
            return Result.of(Map.of());
        }
    }
}
