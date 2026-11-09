package io.casehub.yaml.test;

import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import io.casehub.yaml.step.plugin.TestExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RunPlaybookPluginTest {

    private final RunPlaybookPlugin plugin = new RunPlaybookPlugin();

    @Test
    void executesSimplePlaybookAndReportsSuccess(@TempDir Path tempDir) throws IOException {
        var registry = new CompositePluginRegistry();
        registry.register(Definition.of("echo")
                .execute((params, svc) -> Result.of(Map.of("value", params.get("text"))))
                .build());

        Path playbook = tempDir.resolve("simple.yaml");
        Files.writeString(playbook, """
                name: simple test
                steps:
                  - echo:
                      text: hello
                """);

        var services = buildServices(registry, tempDir);
        var params = Map.<String, Object>of("_subject", "simple.yaml");

        Result result = plugin.execute(params, services);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output().get("succeeded")).isEqualTo(true);
        assertThat(result.output().get("stepCount")).isEqualTo(1);
    }

    @Test
    void reportsFailureWhenStepFails(@TempDir Path tempDir) throws IOException {
        var registry = new CompositePluginRegistry();
        registry.register(Definition.of("fail-step")
                .execute((params, svc) -> Result.failed("intentional failure"))
                .build());

        Path playbook = tempDir.resolve("failing.yaml");
        Files.writeString(playbook, """
                name: failing test
                steps:
                  - fail-step: {}
                """);

        var services = buildServices(registry, tempDir);
        var params = Map.<String, Object>of("_subject", "failing.yaml");

        Result result = plugin.execute(params, services);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output().get("succeeded")).isEqualTo(false);
        assertThat(result.output().get("error")).isEqualTo("intentional failure");
    }

    @Test
    void failsWhenFileNotFound(@TempDir Path tempDir) {
        var registry = new CompositePluginRegistry();
        var services = buildServices(registry, tempDir);
        var params = Map.<String, Object>of("_subject", "nonexistent.yaml");

        Result result = plugin.execute(params, services);
        assertThat(result.isSuccess()).isFalse();
        assertThat(((Result.Failure) result).message()).contains("not found");
    }

    @Test
    void usesFileParamWhenNoSubject(@TempDir Path tempDir) throws IOException {
        var registry = new CompositePluginRegistry();
        registry.register(Definition.of("noop")
                .execute((params, svc) -> Result.of(Map.of()))
                .build());

        Path playbook = tempDir.resolve("noop.yaml");
        Files.writeString(playbook, """
                steps:
                  - noop: {}
                """);

        var services = buildServices(registry, tempDir);
        var params = Map.<String, Object>of("file", "noop.yaml");

        Result result = plugin.execute(params, services);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void exposesNamedCapturesInResult(@TempDir Path tempDir) throws IOException {
        var registry = new CompositePluginRegistry();
        registry.register(Definition.of("echo")
                                    .execute((params, svc) -> Result.of(Map.of("value", params.get("text"))))
                                    .build());

        Path playbook = tempDir.resolve("captures.yaml");
        Files.writeString(playbook, """
                                    name: captures test
                                    steps:
                                      - echo:
                                          text: alpha
                                        capture: first
                                      - echo:
                                          text: beta
                                        capture: second
                                    """);

        var services = buildServices(registry, tempDir);
        var params   = Map.<String, Object>of("_subject", "captures.yaml");

        Result result = plugin.execute(params, services);
        assertThat(result.isSuccess()).isTrue();

        var captures = (Map<String, Map<String, Object>>) result.output().get("captures");
        assertThat(captures).isNotNull();
        assertThat(captures).containsKey("first");
        assertThat(captures).containsKey("second");
        assertThat(captures.get("first")).containsEntry("value", "alpha");
        assertThat(captures.get("second")).containsEntry("value", "beta");
    }


    private MapServiceRegistry buildServices(CompositePluginRegistry registry, Path root) {
        var services = new MapServiceRegistry();
        var ctx = new TestExecutionContext(registry, services, root,
                                           new VariableResolver(Map.of(), java.util.Set.of()), null);
        services.register(TestExecutionContext.class, ctx);
        return services;
    }
}
