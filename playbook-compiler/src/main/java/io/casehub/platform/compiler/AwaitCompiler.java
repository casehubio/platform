package io.casehub.platform.compiler;

import org.openworkflow.sdk.api.types.SetTask;
import org.openworkflow.sdk.api.types.Task;
import org.openworkflow.sdk.api.types.TaskItem;

import java.util.Map;

class AwaitCompiler implements StepCompiler {

    @Override
    @SuppressWarnings("unchecked")
    public TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep, CompilationContext ctx) {
        Map<String, Object> config = (Map<String, Object>) stepValue;
        String taskName;
        if (config.containsKey("signal")) {
            String signalName = String.valueOf(config.get("signal"));
            taskName = "signal_recv_" + signalName;
        } else if (config.containsKey("barrier")) {
            String barrierName = String.valueOf(config.get("barrier"));
            taskName = "barrier_" + barrierName + "_" + ctx.stepIndex();
        } else {
            ctx.addDiagnostic(CompilationDiagnostic.Severity.ERROR,
                    "await requires 'signal' or 'barrier' key");
            return null;
        }
        ctx.recordSource(taskName);
        return new TaskItem(taskName, new Task().withSetTask(new SetTask()));
    }
}
