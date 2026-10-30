package io.casehub.platform.compiler;

import io.serverlessworkflow.api.types.Set;
import io.serverlessworkflow.api.types.SetTask;
import io.serverlessworkflow.api.types.SetTaskConfiguration;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;

import java.util.Map;

class AdvisoryCompiler implements StepCompiler {

    private static final java.util.Set<String> REQUIRED_FIELDS =
            java.util.Set.of("severity", "message", "action");

    @Override
    @SuppressWarnings("unchecked")
    public TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep,
                            CompilationContext ctx) {
        if (!(stepValue instanceof Map)) {
            ctx.addDiagnostic(CompilationDiagnostic.Severity.ERROR,
                    "advisory requires a map with severity, message, and action");
            return null;
        }

        Map<String, Object> config = (Map<String, Object>) stepValue;

        for (String field : REQUIRED_FIELDS) {
            if (!config.containsKey(field)) {
                ctx.addDiagnostic(CompilationDiagnostic.Severity.ERROR,
                        "advisory missing required field: " + field);
                return null;
            }
        }

        String taskName = ctx.generateName("advisory");

        var setConfig = new SetTaskConfiguration()
                .withAdditionalProperty("severity", String.valueOf(config.get("severity")))
                .withAdditionalProperty("message", String.valueOf(config.get("message")))
                .withAdditionalProperty("action", String.valueOf(config.get("action")));

        var setTask = new SetTask()
                .withSet(new Set().withSetTaskConfiguration(setConfig));

        return new TaskItem(taskName, new Task().withSetTask(setTask));
    }
}
