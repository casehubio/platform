package io.casehub.yaml.test.engine;

import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import io.casehub.yaml.step.plugin.AssertThatPlugin;
import io.casehub.yaml.step.plugin.EmitEventPlugin;
import io.casehub.yaml.test.TestResult;
import io.casehub.yaml.test.TestStatus;
import io.casehub.yaml.test.YamlTestDiscovery;
import io.casehub.yaml.test.YamlTestExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TutorialIntegrationTest {

    private static final Path TUTORIAL_ROOT = Path.of("src/test/resources/tutorials");

    private CompositePluginRegistry pluginRegistry;
    private MapServiceRegistry serviceRegistry;

    @BeforeEach
    void setUp() {
        pluginRegistry = new CompositePluginRegistry();
        pluginRegistry.register(AssertThatPlugin.definition());
        pluginRegistry.register(EmitEventPlugin.definition());
        pluginRegistry.register(mockAgentInvoke());
        pluginRegistry.register(mockAssert());
    }

    @Test
    void allTutorialTestsPass() {
        serviceRegistry = new MapServiceRegistry();
        serviceRegistry.register(io.casehub.platform.api.expression.ExpressionEngineRegistry.class,
                                 new io.casehub.yaml.test.DotPathExpressionEngine());
        var executor = new YamlTestExecutor(pluginRegistry, serviceRegistry, TUTORIAL_ROOT);
        var descriptors = YamlTestDiscovery.discover(TUTORIAL_ROOT);

        assertThat(descriptors).as("Should discover tutorial files").isNotEmpty();

        List<TestResult> allResults = new ArrayList<>();
        for (var descriptor : descriptors) {
            List<TestResult> results = executor.execute(descriptor);
            allResults.addAll(results);
        }

        assertThat(allResults).as("Should have test results").isNotEmpty();

        var failures = allResults.stream()
                .filter(r -> r.status() != TestStatus.PASS)
                .toList();

        if (!failures.isEmpty()) {
            var sb = new StringBuilder("Tutorial failures:\n");
            for (var f : failures) {
                sb.append("  ").append(f.status()).append(": ").append(f.name());
                sb.append(" (").append(f.file()).append(")\n");
                for (var failure : f.failures()) {
                    sb.append("    ").append(failure.message().replace("\n", "\n    ")).append("\n");
                }
            }
            org.junit.jupiter.api.Assertions.fail(sb.toString());
        }
    }

    @Test
    void basicsDirectoryHasThreeFiles() {
        var descriptors = YamlTestDiscovery.discover(TUTORIAL_ROOT.resolve("01-basics"));
        assertThat(descriptors).hasSize(3);
    }

    @Test
    void multiTestFileProducesMultipleResults() {
        serviceRegistry = new MapServiceRegistry();
        serviceRegistry.register(io.casehub.platform.api.expression.ExpressionEngineRegistry.class,
                                 new io.casehub.yaml.test.DotPathExpressionEngine());
        var executor = new YamlTestExecutor(pluginRegistry, serviceRegistry, TUTORIAL_ROOT);
        var descriptors = YamlTestDiscovery.discover(TUTORIAL_ROOT.resolve("01-basics"));

        var multiFile = descriptors.stream()
                .filter(d -> d.name().equals("Multi-test basics"))
                .findFirst()
                .orElseThrow();

        List<TestResult> results = executor.execute(multiFile);
        assertThat(results).hasSize(2);
        assertThat(results).extracting(TestResult::name)
                .containsExactly("First invocation", "Second invocation");
        assertThat(results).allMatch(r -> r.status() == TestStatus.PASS);
    }

    private static Definition mockAgentInvoke() {
        return Definition.of("agent-invoke")
                .description("Mock agent invoke for tutorials")
                .execute((params, services) -> {
                    String prompt = String.valueOf(params.getOrDefault("prompt",
                            params.getOrDefault("_subject", "")));
                    return Result.of(Map.of(
                            "text", "Simulated response for: " + prompt,
                            "tokens", 10,
                            "duration", 50
                    ));
                })
                .build();
    }

    private static Definition mockAssert() {
        return Definition.of("assert")
                .execute((params, services) -> {
                    Object expr = params.get("expression");
                    if (Boolean.TRUE.equals(expr) || "true".equals(String.valueOf(expr))) {
                        return Result.of(Map.of("passed", true));
                    }
                    return Result.failed("Assertion failed: " + expr);
                })
                .build();
    }
}
