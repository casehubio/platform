package io.casehub.yaml.test;

import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import io.casehub.yaml.step.plugin.AssertThatPlugin;
import io.casehub.yaml.step.plugin.EmitEventPlugin;
import io.casehub.yaml.step.plugin.EventSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class YamlTestExecutorTest {

    private static final Path SUITE_ROOT = Path.of("src/test/resources/test-suite");

    private CompositePluginRegistry pluginRegistry;
    private MapServiceRegistry serviceRegistry;

    @BeforeEach
    void setUp() {
        pluginRegistry = new CompositePluginRegistry();
        pluginRegistry.register(AssertThatPlugin.definition());
        pluginRegistry.register(EmitEventPlugin.definition());
        pluginRegistry.register(agentInvokeMock());
        pluginRegistry.register(Definition.of("assert")
                .execute((params, svc) -> {
                    Object expr = params.get("expression");
                    if (Boolean.TRUE.equals(expr) || "true".equals(String.valueOf(expr))) {
                        return Result.of(Map.of("passed", true));
                    }
                    return Result.failed("Assertion failed: " + expr);
                })
                .build());

        serviceRegistry = new MapServiceRegistry();
        serviceRegistry.register(ExpressionEngineRegistry.class, new DotPathExpressionEngine());
    }

    @Test
    void executeSingleTestFilePassesWithSimulation() {
        var descriptors = YamlTestDiscovery.discover(SUITE_ROOT);
        var singleTestFile = descriptors.get(0);

        var executor = new YamlTestExecutor(pluginRegistry, serviceRegistry, SUITE_ROOT);
        List<TestResult> results = executor.execute(singleTestFile);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(TestStatus.PASS);
        assertThat(results.get(0).name()).isEqualTo("Basic assertion test");
    }

    @Test
    void executeMultiTestFileProducesMultipleResults() {
        var descriptors = YamlTestDiscovery.discover(SUITE_ROOT);
        var multiTestFile = descriptors.get(1);

        var executor = new YamlTestExecutor(pluginRegistry, serviceRegistry, SUITE_ROOT);
        List<TestResult> results = executor.execute(multiTestFile);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(TestResult::name)
                .containsExactly("First test", "Second test");
        assertThat(results).allMatch(r -> r.status() == TestStatus.PASS);
    }

    @Test
    void failingAssertionProducesFailResult() {
        var testCase = new TestCase("Failing test", null,
                List.of(Map.of("assertThat", "actual value", "contains", "missing")),
                null);
        var descriptor = new TestFileDescriptor("Failing test",
                Path.of("test.yaml"), null, null, null, List.of(testCase));

        var executor = new YamlTestExecutor(pluginRegistry, serviceRegistry, SUITE_ROOT);
        List<TestResult> results = executor.execute(descriptor);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(TestStatus.FAIL);
        assertThat(results.get(0).failures()).isNotEmpty();
        assertThat(results.get(0).failures().get(0).message()).contains("contains");
    }

    @Test
    void emitEventCapturedDuringExecution() {
        var testCase = new TestCase("Event test", null,
                List.of(
                        Map.of("emit-event", "test.ping", "data", Map.of("key", "val")),
                        Map.of("assert", Map.of("expression", "true"))
                ),
                null);
        var descriptor = new TestFileDescriptor("Event test",
                Path.of("test.yaml"), null, null, null, List.of(testCase));

        var executor = new YamlTestExecutor(pluginRegistry, serviceRegistry, SUITE_ROOT);
        List<TestResult> results = executor.execute(descriptor);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(TestStatus.PASS);
    }

    @Test
    void captureDecoratorBindsOutputToVariable() {
        var testCase = new TestCase("Capture test", null,
                List.of(
                        Map.of("emit-event", "test.ping", "data", Map.of("key", "val"),
                                "capture", "emitted"),
                        Map.of("assert", Map.of("expression", "true"))
                ),
                null);
        var descriptor = new TestFileDescriptor("Capture test",
                Path.of("test.yaml"), null, null, null, List.of(testCase));

        var executor = new YamlTestExecutor(pluginRegistry, serviceRegistry, SUITE_ROOT);
        List<TestResult> results = executor.execute(descriptor);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(TestStatus.PASS);
    }

    private static Definition agentInvokeMock() {
        return Definition.of("agent-invoke")
                .description("Mock agent invoke")
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
}
