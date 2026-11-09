package io.casehub.platform.compiler;

import io.serverlessworkflow.api.types.Set;
import io.serverlessworkflow.api.types.SetTask;
import io.serverlessworkflow.api.types.SetTaskConfiguration;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;

import java.util.Map;

class McpCallStepCompiler implements StepCompiler {

    @Override
    @SuppressWarnings("unchecked")
    public TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep,
                            CompilationContext ctx) {
        String tool;
        Map<String, Object> params = Map.of();

        if (stepValue instanceof String s) {
            tool = s;
        } else if (stepValue instanceof Map) {
            Map<String, Object> config = new java.util.LinkedHashMap<>((Map<String, Object>) stepValue);
            if (config.containsKey("_subject")) {
                config.putIfAbsent("tool", String.valueOf(config.remove("_subject")));
            }
            config.remove("_value");
            tool = (String) config.get("tool");
            if (tool == null) {
                ctx.addDiagnostic(CompilationDiagnostic.Severity.ERROR,
                        "mcp-call missing required field: tool");
                return null;
            }
            if (config.containsKey("params")) {
                params = (Map<String, Object>) config.get("params");
            }
        } else {
            ctx.addDiagnostic(CompilationDiagnostic.Severity.ERROR,
                    "mcp-call requires a string or map");
            return null;
        }

        String taskName = ctx.generateName("mcp-call");
        var setConfig = new SetTaskConfiguration()
                .withAdditionalProperty("_stepType", "mcp-call")
                .withAdditionalProperty("tool", tool);
        if (!params.isEmpty()) {
            setConfig.withAdditionalProperty("params", params);
        }

        return new TaskItem(taskName, new Task().withSetTask(
                new SetTask().withSet(new Set().withSetTaskConfiguration(setConfig))));
    }
}
