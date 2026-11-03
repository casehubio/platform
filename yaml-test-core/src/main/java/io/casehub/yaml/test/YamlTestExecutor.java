package io.casehub.yaml.test;

import io.casehub.platform.simulation.SimulationOverlay;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.plugin.api.ServiceRegistry;
import io.casehub.yaml.step.catalog.ResolvedStep;
import io.casehub.yaml.step.catalog.Walker;
import io.casehub.yaml.step.eval.CatalogStepRunner;
import io.casehub.yaml.step.eval.StructuralStepEvaluator;
import io.casehub.yaml.step.plugin.EventSink;
import io.casehub.yaml.step.plugin.TestExecutionContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class YamlTestExecutor {

    private static final Set<String> RESERVED_CAPTURE_NAMES = Set.of("events");
    private static final io.casehub.yaml.core.condition.ConditionEvaluator CONDITION_EVALUATOR =
            new io.casehub.yaml.core.condition.ConditionEvaluator(null);

    private final PluginRegistry pluginRegistry;
    private final ServiceRegistry baseServiceRegistry;
    private final Path resourceRoot;
    private final SimulationRuntime simulationRuntime;

    public YamlTestExecutor(PluginRegistry pluginRegistry, ServiceRegistry baseServiceRegistry,
                            Path resourceRoot) {
        this(pluginRegistry, baseServiceRegistry, resourceRoot, null);
    }

    public YamlTestExecutor(PluginRegistry pluginRegistry, ServiceRegistry baseServiceRegistry,
                            Path resourceRoot, SimulationRuntime simulationRuntime) {
        this.pluginRegistry = pluginRegistry;
        this.baseServiceRegistry = baseServiceRegistry;
        this.resourceRoot = resourceRoot;
        this.simulationRuntime = simulationRuntime;
    }

    public List<TestResult> execute(TestFileDescriptor fileDescriptor) {
        SimulationOverlay overlay = null;
        try {
            if (fileDescriptor.simulateBlock() != null && simulationRuntime != null) {
                var parsed = SimulateBlockParser.parse(fileDescriptor.simulateBlock(), resourceRoot);
                overlay = simulationRuntime.pushOverlay(parsed.config(), parsed.corpus());
            }

            runLifecycleSteps(fileDescriptor.fileSetup(), "file-setup");

            List<TestResult> results = new ArrayList<>(fileDescriptor.testCases().size());
            for (var testCase : fileDescriptor.testCases()) {
                results.add(executeTestCase(testCase, fileDescriptor.file()));
            }
            return results;
        } catch (Exception e) {
            return List.of(TestResult.error(fileDescriptor.name(), fileDescriptor.file(), 0,
                    "File-level error: " + e.getMessage()));
        } finally {
            try {
                runLifecycleSteps(fileDescriptor.fileTeardown(), "file-teardown");
            } catch (Exception ignored) {}
            if (overlay != null) {
                simulationRuntime.popOverlay(overlay);
            }
        }
    }

    private TestResult executeTestCase(TestCase testCase, Path file) {
        long start = System.nanoTime();
        EventCapture eventCapture = new EventCapture();

        var testServices = new MapServiceRegistry();
        testServices.register(EventSink.class, (EventSink) eventCapture::addEvent);

        var testContext = new TestExecutionContext(pluginRegistry, testServices, resourceRoot, null);
        testServices.register(TestExecutionContext.class, testContext);

        VariableResolver resolver = new VariableResolver(Map.of(), Set.of("events"));

        try {
            runLifecycleSteps(testCase.setupSteps(), "per-test-setup");

            List<ResolvedStep> resolvedSteps = Walker.resolve(testCase.steps(), pluginRegistry);
            var runner = new CatalogStepRunner(pluginRegistry, testServices);
            var evaluator = new StructuralStepEvaluator(CONDITION_EVALUATOR);

            List<TestFailure> failures = new ArrayList<>();
            int stepIndex = 0;

            for (var step : resolvedSteps) {
                stepIndex++;
                Result result = evaluator.evaluate(step, resolver, runner);

                if (!result.isSuccess()) {
                    String stepName = step instanceof ResolvedStep.PluginStep ps ? ps.actionName() : "structural";
                    String message = result instanceof Result.Failure f ? f.message() : "Step failed";
                    failures.add(new TestFailure(stepIndex, stepName, message));
                }

                if (step instanceof ResolvedStep.PluginStep ps) {
                    Object captureKey = ps.decorators().get("capture");
                    if (captureKey != null && result.isSuccess() && result.output() != null) {
                        String captureName = String.valueOf(captureKey);
                        if (RESERVED_CAPTURE_NAMES.contains(captureName)) {
                            failures.add(new TestFailure(stepIndex, ps.actionName(),
                                    "'" + captureName + "' is a reserved capture name"));
                        } else {
                            Map<String, Object> capturedOutput = result.output();
                            resolver = resolver.withObjectScope(captureName, capturedOutput::get);
                        }
                    }
                }
            }

            long durationMs = (System.nanoTime() - start) / 1_000_000;
            if (failures.isEmpty()) {
                return TestResult.pass(testCase.name(), file, durationMs);
            }
            return TestResult.fail(testCase.name(), file, durationMs, failures);
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            return TestResult.error(testCase.name(), file, durationMs, e.getMessage());
        } finally {
            try {
                runLifecycleSteps(testCase.teardownSteps(), "per-test-teardown");
            } catch (Exception ignored) {}
        }
    }

    private void runLifecycleSteps(List<Map<String, Object>> steps, String context) {
        if (steps == null || steps.isEmpty()) return;

        var lifecycleServices = new MapServiceRegistry();
        var eventCapture = new EventCapture();
        lifecycleServices.register(EventSink.class, (EventSink) eventCapture::addEvent);

        List<ResolvedStep> resolved = Walker.resolve(steps, pluginRegistry);
        var runner = new CatalogStepRunner(pluginRegistry, lifecycleServices);
        var evaluator = new StructuralStepEvaluator(CONDITION_EVALUATOR);
        var resolver = new VariableResolver(Map.of(), Set.of());

        for (var step : resolved) {
            Result result = evaluator.evaluate(step, resolver, runner);
            if (!result.isSuccess()) {
                String msg = result instanceof Result.Failure f ? f.message() : "Lifecycle step failed";
                throw new RuntimeException(context + ": " + msg);
            }
        }
    }
}
