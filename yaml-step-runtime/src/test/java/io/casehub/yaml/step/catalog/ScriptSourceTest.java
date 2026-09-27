package io.casehub.yaml.step.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.platform.api.process.DefaultProcessExecutor;
import io.casehub.platform.api.process.ProcessExecutor;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.step.CatalogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptSourceTest {

    private Path testDir;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ProcessExecutor executor = new DefaultProcessExecutor();

    @BeforeEach
    void setUp() {
        testDir = Path.of(getClass().getClassLoader().getResource("scripts/sentiment.py").getPath()).getParent();
    }

    @Test
    void discoversScriptsWithCompanionSchema() {
        var source = new ScriptSource(List.of(testDir), yamlMapper, executor);
        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        source.populate(entries);

        assertThat(entries).containsKey("sentiment");
        assertThat(entries.get("sentiment").definition().invoke())
                .isInstanceOf(InvokeBinding.Script.class);
        var binding = (InvokeBinding.Script) entries.get("sentiment").definition().invoke();
        assertThat(binding.runtime()).isEqualTo("python3");
        assertThat(binding.script()).endsWith("sentiment.py");
    }

    @Test
    void detectsNodeRuntimeFromExtension() {
        var source = new ScriptSource(List.of(testDir), yamlMapper, executor);
        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        source.populate(entries);

        assertThat(entries).containsKey("transform");
        var binding = (InvokeBinding.Script) entries.get("transform").definition().invoke();
        assertThat(binding.runtime()).isEqualTo("node");
    }

    @Test
    void skipsScriptsWithoutSchema() {
        var source = new ScriptSource(List.of(testDir), yamlMapper, executor);
        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        source.populate(entries);

        assertThat(entries).doesNotContainKey("orphan");
    }

    @Test
    void parsesSchemaInputsAndOutputs() {
        var source = new ScriptSource(List.of(testDir), yamlMapper, executor);
        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        source.populate(entries);

        var def = entries.get("sentiment").definition();
        assertThat(def.inputs()).containsKey("text");
        assertThat(def.inputs().get("text").required()).isTrue();
        assertThat(def.outputs()).containsKey("score");
        assertThat(def.description()).isEqualTo("Analyze text sentiment");
    }

    @Test
    void priorityIs400() {
        var source = new ScriptSource(List.of(testDir), yamlMapper, executor);
        assertThat(source.priority()).isEqualTo(400);
    }

    @Test
    void emptyPathsProducesNoEntries() {
        var source = new ScriptSource(List.of(), yamlMapper, executor);
        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        source.populate(entries);
        assertThat(entries).isEmpty();
    }

    @Test
    void nonExistentPathIsSkipped() {
        var source = new ScriptSource(List.of(Path.of("/nonexistent/path")), yamlMapper, executor);
        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        source.populate(entries);
        assertThat(entries).isEmpty();
    }
}
