package io.casehub.yaml.test;

import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import io.casehub.yaml.step.plugin.TestExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatePlaybookPluginTest {

    @TempDir Path tempDir;

    @Test
    void validPlaybook_returnsCompilationMetadata() throws Exception {
        Path examplesDir = findExamplesDir();
        org.junit.jupiter.api.Assumptions.assumeTrue(
                examplesDir != null && Files.isDirectory(examplesDir),
                "Example playbooks directory not found");

        var services = new MapServiceRegistry();
        var pluginRegistry = new CompositePluginRegistry();
        registerStubActions(pluginRegistry);

        var ctx = new TestExecutionContext(pluginRegistry, services, examplesDir, null, null);
        services.register(TestExecutionContext.class, ctx);

        var plugin = new ValidatePlaybookPlugin();
        Result result = plugin.execute(Map.of("_subject", "01-direct-invoke.yaml"), services);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output().get("valid")).isEqualTo(true);
        assertThat(result.output().get("stepCount")).isInstanceOf(Integer.class);
        assertThat((int) result.output().get("stepCount")).isEqualTo(3);
    }

    @Test
    void missingFile_returnsError() {
        var services = new MapServiceRegistry();
        var pluginRegistry = new CompositePluginRegistry();
        var ctx = new TestExecutionContext(pluginRegistry, services, tempDir, null, null);
        services.register(TestExecutionContext.class, ctx);

        var plugin = new ValidatePlaybookPlugin();
        Result result = plugin.execute(Map.of("_subject", "nonexistent.yaml"), services);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void playbookWithPools_returnsPoolMetadata() throws Exception {
        Path examplesDir = findExamplesDir();
        org.junit.jupiter.api.Assumptions.assumeTrue(
                examplesDir != null && Files.isDirectory(examplesDir),
                "Example playbooks directory not found");

        var services = new MapServiceRegistry();
        var pluginRegistry = new CompositePluginRegistry();
        registerStubActions(pluginRegistry);

        var ctx = new TestExecutionContext(pluginRegistry, services, examplesDir, null, null);
        services.register(TestExecutionContext.class, ctx);

        var plugin = new ValidatePlaybookPlugin();
        Result result = plugin.execute(
                Map.of("_subject", "05-pool-backed.yaml"), services);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output().get("valid")).isEqualTo(true);

        var poolDecls = (List<Map<String, Object>>) result.output().get("poolDeclarations");
        assertThat(poolDecls).hasSize(1);
        assertThat(poolDecls.getFirst().get("name")).isEqualTo("analysts");

        var pools = (Map<String, Map<String, Object>>) result.output().get("pools");
        assertThat(pools).containsKey("analysts");
        assertThat(pools.get("analysts").get("maxActive")).isEqualTo(5);
    }

    @Test
    @SuppressWarnings("unchecked")
    void playbookWithFork_detectsParallelBlock() throws Exception {
        Path examplesDir = findExamplesDir();
        org.junit.jupiter.api.Assumptions.assumeTrue(
                examplesDir != null && Files.isDirectory(examplesDir),
                "Example playbooks directory not found");

        var services = new MapServiceRegistry();
        var pluginRegistry = new CompositePluginRegistry();
        registerStubActions(pluginRegistry);

        var ctx = new TestExecutionContext(pluginRegistry, services, examplesDir, null, null);
        services.register(TestExecutionContext.class, ctx);

        var plugin = new ValidatePlaybookPlugin();
        Result result = plugin.execute(
                Map.of("_subject", "04-parallel-queries.yaml"), services);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output().get("hasFork")).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void directoryMode_validatesAllPlaybooks() throws Exception {
        Path playbooksDir = tempDir.resolve("playbooks");
        Files.createDirectories(playbooksDir);

        Files.writeString(playbooksDir.resolve("hello.yaml"), """
                name: hello
                steps:
                  - log: "hello"
                """);
        Files.writeString(playbooksDir.resolve("world.yaml"), """
                name: world
                steps:
                  - log: "world"
                """);

        var pluginRegistry = new CompositePluginRegistry();
        pluginRegistry.register(Definition.of("log")
                .execute((params, svc) -> Result.of(Map.of()))
                .build());

        var services = new MapServiceRegistry();
        var ctx = new TestExecutionContext(pluginRegistry, services, tempDir, null, null);
        services.register(TestExecutionContext.class, ctx);

        var plugin = new ValidatePlaybookPlugin();
        Result result = plugin.execute(
                Map.of("directory", "playbooks"),
                services);

        assertThat(result.isSuccess()).as("Plugin should succeed").isTrue();
        @SuppressWarnings("unchecked")
        var errors = (List<Map<String, Object>>) result.output().get("errors");
        assertThat(errors).as("Validation errors: " + errors).isEmpty();
        assertThat(result.output().get("valid")).isEqualTo(true);
        assertThat(result.output().get("validatedCount")).isEqualTo(2);
    }

    private static Path findExamplesDir() {
        for (String candidate : List.of(
                "../playbooks/src/main/playbooks/agent-interaction-patterns",
                "../../playbooks/src/main/playbooks/agent-interaction-patterns",
                "examples/agent-interaction-patterns",
                "../examples/agent-interaction-patterns")) {
            Path path = Path.of(candidate);
            if (Files.isDirectory(path)) return path;
        }
        return null;
    }

    private static void registerStubActions(CompositePluginRegistry registry) {
        for (String name : List.of("log", "agent-session-open", "agent-session-query",
                "agent-session-clear", "agent-session-close", "agent-invoke",
                "agent-query")) {
            registry.register(Definition.of(name)
                    .execute((params, svc) -> Result.of(Map.of()))
                    .build());
        }
    }
}
