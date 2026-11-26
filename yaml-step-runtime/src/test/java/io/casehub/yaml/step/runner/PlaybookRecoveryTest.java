package io.casehub.yaml.step.runner;

import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybookRecoveryTest {

    @TempDir Path tempDir;

    @Test
    void recovers_from_unmet_requirement_via_setup_playbook() throws IOException {
        var callCount = new AtomicInteger(0);
        Action requireAction = (params, services) -> {
            if (callCount.getAndIncrement() == 0) {
                return Result.failed("sms not ready", "unmet-requirement",
                        Map.of("capability", "sms", "sub", List.of(),
                                "options", List.of(
                                        Map.of("type", "provider", "connector-id", "numbrrs",
                                                "setup-playbook", "setup.yaml"))));
            }
            return Result.of(Map.of("satisfied", true));
        };

        Files.writeString(tempDir.resolve("setup.yaml"), "name: setup\n---\n- configure:\n    provider: numbrrs\n");

        Action configureAction = (params, services) -> Result.of(Map.of("configured", true));

        var registry = registryWith(
                definition("require-capability", requireAction),
                definition("configure", configureAction));

        var runner = new PlaybookRunnerCore(registry, new MapServiceRegistry(),
                tempDir, null, null, null, List.of(new UnmetRequirementHandler()));

        var mainPlaybook = tempDir.resolve("main.yaml");
        Files.writeString(mainPlaybook, "name: main\n---\n- require-capability:\n    capability: sms\n");

        var result = runner.run(mainPlaybook, PlaybookRunConfig.defaults());

        assertThat(result.error()).as("run error").isNull();
        assertThat(result.success()).isTrue();
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void fails_when_no_handler_registered() throws IOException {
        Action requireAction = (params, services) ->
                Result.failed("sms not ready", "unmet-requirement",
                        Map.of("capability", "sms", "options", List.of(
                                Map.of("type", "provider", "setup-playbook", "setup.yaml"))));

        var registry = registryWith(definition("require-capability", requireAction));

        var runner = new PlaybookRunnerCore(registry, new MapServiceRegistry(),
                tempDir, null, null, null, List.of());

        var mainPlaybook = tempDir.resolve("main.yaml");
        Files.writeString(mainPlaybook, "name: test\n---\n- require-capability:\n    capability: sms\n");

        var result = runner.run(mainPlaybook, new PlaybookRunConfig(false, Map.of(), null, null));

        assertThat(result.success()).isFalse();
    }

    @Test
    void stops_at_depth_limit() throws IOException {
        Action alwaysFailAction = (params, services) ->
                Result.failed("not ready", "unmet-requirement",
                        Map.of("capability", "x", "options", List.of(
                                Map.of("type", "provider", "connector-id", "p",
                                        "setup-playbook", "deeper.yaml"))));

        Files.writeString(tempDir.resolve("deeper.yaml"),
                "name: test\n---\n- require-capability:\n    capability: x\n");

        var registry = registryWith(definition("require-capability", alwaysFailAction));

        var runner = new PlaybookRunnerCore(registry, new MapServiceRegistry(),
                tempDir, null, null, null, List.of(new UnmetRequirementHandler()));

        var mainPlaybook = tempDir.resolve("main.yaml");
        Files.writeString(mainPlaybook, "name: test\n---\n- require-capability:\n    capability: x\n");

        var result = runner.run(mainPlaybook, new PlaybookRunConfig(false, Map.of(), null, null));

        assertThat(result.success()).isFalse();
    }

    @Test
    void detects_cycle() throws IOException {
        var callCount = new AtomicInteger(0);
        Action failAction = (params, services) -> {
            String setupPath = callCount.getAndIncrement() == 0 ? "b.yaml" : "main.yaml";
            return Result.failed("not ready", "unmet-requirement",
                    Map.of("capability", "x", "options", List.of(
                            Map.of("type", "provider", "connector-id", "p",
                                    "setup-playbook", setupPath))));
        };

        Files.writeString(tempDir.resolve("b.yaml"),
                "name: test\n---\n- require-capability:\n    capability: x\n");

        var registry = registryWith(definition("require-capability", failAction));

        var runner = new PlaybookRunnerCore(registry, new MapServiceRegistry(),
                tempDir, null, null, null, List.of(new UnmetRequirementHandler()));

        var mainPlaybook = tempDir.resolve("main.yaml");
        Files.writeString(mainPlaybook, "name: test\n---\n- require-capability:\n    capability: x\n");

        var result = runner.run(mainPlaybook, new PlaybookRunConfig(false, Map.of(), null, null));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("Circular recovery");
    }

    @Test
    void manual_only_returns_enriched_error() throws IOException {
        Action requireAction = (params, services) ->
                Result.failed("sms not ready", "unmet-requirement",
                        Map.of("capability", "sms", "sub", List.of("receive"),
                                "options", List.of(Map.of("type", "manual", "capability", "sms"))));

        var registry = registryWith(definition("require-capability", requireAction));

        var runner = new PlaybookRunnerCore(registry, new MapServiceRegistry(),
                tempDir, null, null, null, List.of(new UnmetRequirementHandler()));

        var mainPlaybook = tempDir.resolve("main.yaml");
        Files.writeString(mainPlaybook, "name: test\n---\n- require-capability:\n    capability: sms\n");

        var result = runner.run(mainPlaybook, new PlaybookRunConfig(false, Map.of(), null, null));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("manual resolution");
    }

    private static PluginRegistry registryWith(Definition... defs) {
        var registry = new SimplePluginRegistry();
        for (var def : defs) {
            registry.register(def);
        }
        return registry;
    }

    private static Definition definition(String name, Action action) {
        return Definition.of(name).execute(action).absorbSiblings(true).build();
    }

    private static class SimplePluginRegistry implements PluginRegistry {
        private final java.util.concurrent.ConcurrentHashMap<String, Definition> defs = new java.util.concurrent.ConcurrentHashMap<>();
        @Override public void register(Definition definition) { defs.put(definition.name(), definition); }
        @Override public Optional<Definition> unregister(String actionName) { return Optional.ofNullable(defs.remove(actionName)); }
        @Override public Optional<Definition> resolve(String actionName) { return Optional.ofNullable(defs.get(actionName)); }
        @Override public Set<String> availableActions() { return defs.keySet(); }
    }
}
