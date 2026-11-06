package io.casehub.platform.compiler;

import io.serverlessworkflow.api.types.Set;
import io.serverlessworkflow.api.types.SetTask;
import io.serverlessworkflow.api.types.SetTaskConfiguration;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;

import java.util.Map;

class GraphqlExecStepCompiler implements StepCompiler {

    @Override
    @SuppressWarnings("unchecked")
    public TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep,
                            CompilationContext ctx) {
        String query;
        if (stepValue instanceof String s) {
            query = s;
        } else if (stepValue instanceof Map) {
            Map<String, Object> config = (Map<String, Object>) stepValue;
            query = (String) config.get("query");
            if (query == null) {
                ctx.addDiagnostic(CompilationDiagnostic.Severity.ERROR,
                        "graphql-exec missing required field: query");
                return null;
            }
        } else {
            ctx.addDiagnostic(CompilationDiagnostic.Severity.ERROR,
                    "graphql-exec requires a string or map");
            return null;
        }

        String taskName = ctx.generateName("graphql-exec");
        var setConfig = new SetTaskConfiguration()
                .withAdditionalProperty("_stepType", "graphql-exec")
                .withAdditionalProperty("query", query);

        return new TaskItem(taskName, new Task().withSetTask(
                new SetTask().withSet(new Set().withSetTaskConfiguration(setConfig))));
    }
}
