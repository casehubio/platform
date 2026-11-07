package io.casehub.yaml.test;

import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.plugin.api.ServiceRegistry;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.yaml.step.plugin.TestExecutionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

        Map<String, Object> callerVars = params.containsKey("vars")
                                         ? (Map<String, Object>) params.get("vars")
                                         : Map.of();

        SimulationRuntime simRuntime = ctx.simulationRuntime() instanceof SimulationRuntime sr ? sr : null;
        boolean           dryRun     = simRuntime != null;

        var config = new PlaybookRunConfig(dryRun, callerVars, null, null);
        var runner = new PlaybookRunnerCore(ctx.pluginRegistry(), ctx.serviceRegistry(),
                                            ctx.resourceRoot(), simRuntime, new FileSystemModuleLoader());

        PlaybookRunResult runResult = runner.run(playbookPath, config);

        var stepEvents = new ArrayList<Map<String, Object>>();
        if (runResult.trace() != null) {
            for (var entry : runResult.trace().flatten()) {
                var node = entry.node();
                if (!"playbook".equals(node.type())) {
                    stepEvents.add(Map.of(
                            "step", node.index() + 1,
                            "action", node.action() != null ? node.action() : node.type(),
                            "success", node.outcome() == StepOutcome.EXECUTED
                                       || node.outcome() == StepOutcome.SIMULATED));
                }
            }
        }

        var resultMap = new java.util.LinkedHashMap<String, Object>();
        resultMap.put("succeeded", runResult.success());
        resultMap.put("output", runResult.output());
        resultMap.put("stepCount", stepEvents.size());
        resultMap.put("duration", runResult.durationMs());
        resultMap.put("stepEvents", stepEvents);
        resultMap.put("error", runResult.error() != null ? runResult.error() : "");
        if (simRuntime != null) {
            var smStates = simRuntime.stateMachineStates();
            var sanitized = new java.util.LinkedHashMap<String, Object>();
            for (var smEntry : smStates.entrySet()) {
                sanitized.put(smEntry.getKey().replace('.', '_'), smEntry.getValue());
            }
            resultMap.put("stateMachines", sanitized);
        }
        return Result.of(resultMap);
    }
}
