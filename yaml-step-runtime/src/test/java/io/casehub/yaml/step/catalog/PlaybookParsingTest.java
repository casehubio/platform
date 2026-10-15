package io.casehub.yaml.step.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Portability;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.handler.AriaInvokeHandler;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybookParsingTest {

    private static final Set<String> STUB_ACTIONS = Set.of(
            "accumulate", "accumulate-get", "agent-delegate", "await",
            "chan-consume", "chan-produce", "concurrent",
            "corr-await", "corr-expect", "corr-send",
            "counter-add", "counter-get",
            "deadline-check", "deadline-create", "deadline-work",
            "fail", "flag-check", "flag-set", "flag-toggle",
            "gauge-cas", "gauge-get", "gauge-set",
            "graphql", "graphql-exec",
            "latch-countdown", "latch-create", "latch-status",
            "log", "map-reader", "map-writer", "mcp-call",
            "pipeline-consume", "process-exec",
            "rest-request", "script-run", "sem-worker", "simulated",
            "sm-create", "sm-state", "sm-transition",
            "spawn-task", "stop", "task-join", "task-poll"
    );

    private static final CompositePluginRegistry REGISTRY = new CompositePluginRegistry();

    static {
        for (String action : STUB_ACTIONS) {
            REGISTRY.register(new Definition(action, null, Map.of(), Map.of(),
                    Portability.BOTH, (params, services) -> Result.of(Map.of()), null));
        }
        new YamlDefinitionSource(
                List.of("definitions/aria-actions.yaml"),
                List.of(new AriaInvokeHandler(OutputStream.nullOutputStream())),
                event -> {}).populate(REGISTRY);
    }

    private static final Yaml SNAKE_YAML = new Yaml();

    @TestFactory
    @SuppressWarnings("unchecked")
    Collection<DynamicTest> allPlaybooksParseThroughWalker() throws IOException {
        Path playbooksDir = findPlaybooksDir();
        Path manifestPath = playbooksDir.resolve("manifest.json");
        assertThat(manifestPath).exists();

        ObjectMapper json = new ObjectMapper();
        Map<String, Object> manifest = json.readValue(manifestPath.toFile(), Map.class);
        List<Map<String, Object>> categories = (List<Map<String, Object>>) manifest.get("categories");

        List<DynamicTest> tests = new ArrayList<>();

        for (Map<String, Object> category : categories) {
            String categoryKey = (String) category.get("key");
            List<Map<String, Object>> scenarios = (List<Map<String, Object>>) category.get("scenarios");

            for (Map<String, Object> scenario : scenarios) {
                String file = (String) scenario.get("file");
                String title = (String) scenario.get("title");
                Path yamlPath = playbooksDir.resolve(file);

                tests.add(DynamicTest.dynamicTest(
                        categoryKey + " / " + title,
                        () -> parsePlaybook(yamlPath)));
            }
        }

        assertThat(tests).as("manifest should contain scenarios").isNotEmpty();
        return tests;
    }

    @SuppressWarnings("unchecked")
    private void parsePlaybook(Path yamlPath) throws IOException {
        assertThat(yamlPath).as("Playbook file must exist: " + yamlPath).exists();

        List<Object> documents = new ArrayList<>();
        try (InputStream is = Files.newInputStream(yamlPath)) {
            for (Object doc : SNAKE_YAML.loadAll(is)) {
                if (doc != null) documents.add(doc);
            }
        }

        assertThat(documents).as("Playbook must have at least 2 YAML documents").hasSizeGreaterThanOrEqualTo(2);

        List<Map<String, Object>> steps = extractSteps(documents);
        if (steps.isEmpty()) return;

        List<ResolvedStep> resolved = Walker.resolve(steps, REGISTRY);
        assertThat(resolved).as("Walker must resolve at least one step").isNotEmpty();
    }

    private static final Set<String> PAGE_COMPOSITION_KEYS = Set.of("pages", "imports", "parameters", "sections", "outputs");

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractSteps(List<Object> documents) {
        for (Object doc : documents) {
            if (doc instanceof Map<?, ?> map && map.containsKey("steps")) {
                return (List<Map<String, Object>>) map.get("steps");
            }
        }

        Object lastDoc = documents.get(documents.size() - 1);
        if (lastDoc instanceof List<?>) {
            return (List<Map<String, Object>>) lastDoc;
        }
        if (lastDoc instanceof Map<?, ?> map
                && !map.containsKey("playbook") && !map.containsKey("meta") && !map.containsKey("schema")) {
            if (map.keySet().stream().anyMatch(k -> PAGE_COMPOSITION_KEYS.contains(k))) {
                return List.of();
            }
            return List.of((Map<String, Object>) map);
        }

        throw new AssertionError("Could not extract steps from playbook: " + documents.size() + " documents");
    }

    private static Path findPlaybooksDir() {
        Path candidate = Path.of("playbooks");
        if (Files.isDirectory(candidate)) return candidate;
        candidate = Path.of("..").resolve("playbooks");
        if (Files.isDirectory(candidate)) return candidate;
        throw new AssertionError("Cannot find playbooks directory");
    }
}
