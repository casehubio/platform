package io.casehub.yaml.step.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.plugin.api.ServiceRegistry;
import io.casehub.yaml.step.catalog.ResolvedStep;
import io.casehub.yaml.step.catalog.Walker;
import io.casehub.yaml.step.eval.CatalogStepRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RunPlaybookPlugin implements Action {

    public static Definition definition() {
        return Definition.of("run-playbook")
                .description("Executes another playbook and captures results")
                .execute(new RunPlaybookPlugin())
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result execute(Map<String, Object> params, ServiceRegistry services) {
        TestExecutionContext ctx;
        try {
            ctx = services.lookup(TestExecutionContext.class);
        } catch (IllegalArgumentException e) {
            return Result.failed("run-playbook requires TestExecutionContext in ServiceRegistry");
        }

        String file = params.containsKey("_subject")
                ? String.valueOf(params.get("_subject"))
                : (String) params.get("file");

        if (file == null || file.isBlank()) {
            return Result.failed("run-playbook requires a file path");
        }

        Path playbookPath = ctx.resourceRoot().resolve(file);
        if (!Files.exists(playbookPath)) {
            return Result.failed("Playbook not found: " + playbookPath);
        }

        try {
            var mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> doc = mapper.readValue(Files.newInputStream(playbookPath), Map.class);

            var steps = (List<Map<String, Object>>) doc.get("steps");
            if (steps == null || steps.isEmpty()) {
                return Result.of(Map.of("succeeded", true, "stepCount", 0, "output", Map.of()));
            }

            List<ResolvedStep> resolved = Walker.resolve(steps, ctx.pluginRegistry());
            var runner = new CatalogStepRunner(ctx.pluginRegistry(), ctx.serviceRegistry());

            long start = System.nanoTime();
            int stepCount = 0;
            Map<String, Object> outputVars = new LinkedHashMap<>();
            List<Map<String, Object>> stepEvents = new ArrayList<>();
            VariableResolver resolver = ctx.variableResolver();

            for (var step : resolved) {
                stepCount++;
                Result stepResult = runner.run(step, resolver);
                stepEvents.add(Map.of(
                        "step", stepCount,
                        "action", step instanceof ResolvedStep.PluginStep ps ? ps.actionName() : "structural",
                        "success", stepResult.isSuccess()
                ));

                if (!stepResult.isSuccess()) {
                    long durationMs = (System.nanoTime() - start) / 1_000_000;
                    String errorMsg = stepResult instanceof Result.Failure f ? f.message() : "Step failed";
                    return Result.of(Map.of(
                            "succeeded", false,
                            "output", outputVars,
                            "stepCount", stepCount,
                            "duration", durationMs,
                            "stepEvents", stepEvents,
                            "error", errorMsg
                    ));
                }

                if (stepResult.output() != null) {
                    outputVars.putAll(stepResult.output());
                }

                if (step instanceof ResolvedStep.PluginStep ps) {
                    var capture = ps.decorators().get("capture");
                    if (capture != null && stepResult.output() != null) {
                        Map<String, Object> capturedOutput = stepResult.output();
                        resolver = resolver.withObjectScope(String.valueOf(capture), capturedOutput::get);
                    }
                }
            }

            long durationMs = (System.nanoTime() - start) / 1_000_000;
            return Result.of(Map.of(
                    "succeeded", true,
                    "output", outputVars,
                    "stepCount", stepCount,
                    "duration", durationMs,
                    "stepEvents", stepEvents,
                    "error", ""
            ));
        } catch (Exception e) {
            return Result.failed("Playbook execution failed: " + e.getMessage());
        }
    }
}
