package io.casehub.platform.compiler;

import io.serverlessworkflow.api.types.Set;
import io.serverlessworkflow.api.types.SetTask;
import io.serverlessworkflow.api.types.SetTaskConfiguration;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;

import java.util.Map;

class AgentSessionStepCompiler implements StepCompiler {

    private final String stepType;

    AgentSessionStepCompiler(String stepType) {
        this.stepType = stepType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep,
                            CompilationContext ctx) {
        Map<String, Object> config;
        if (stepValue instanceof String s) {
            if ("agent-session-open".equals(stepType) || "agent-query".equals(stepType)) {
                ctx.addDiagnostic(CompilationDiagnostic.Severity.ERROR,
                                  stepType + " requires a map (multiple parameters needed)");
                return null;
            }
            config = Map.of("session", s);
        } else if (stepValue instanceof Map) {
            config = (Map<String, Object>) stepValue;
        } else {
            ctx.addDiagnostic(CompilationDiagnostic.Severity.ERROR,
                              stepType + " requires a string or map");
            return null;
        }

        if (!config.containsKey("session")) {
            ctx.addDiagnostic(CompilationDiagnostic.Severity.ERROR,
                              stepType + " missing required field: session");
            return null;
        }

        if ("agent-query".equals(stepType) && !config.containsKey("user")) {
            ctx.addDiagnostic(CompilationDiagnostic.Severity.ERROR,
                              stepType + " missing required field: user");
            return null;
        }

        String taskName = ctx.generateName(stepType);

        var setConfig = new SetTaskConfiguration()
                                .withAdditionalProperty("_stepType", stepType);

        for (var entry : config.entrySet()) {
            Object value = entry.getValue();
            if (value != null) {
                setConfig.withAdditionalProperty(entry.getKey(),
                                                 value instanceof String ? value : String.valueOf(value));
            }
        }

        var setTask = new SetTask()
                              .withSet(new Set().withSetTaskConfiguration(setConfig));

        return new TaskItem(taskName, new Task().withSetTask(setTask));
    }
}
