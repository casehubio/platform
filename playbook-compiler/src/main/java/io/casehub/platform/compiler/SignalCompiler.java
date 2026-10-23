package io.casehub.platform.compiler;

import org.openworkflow.sdk.api.types.SetTask;
import org.openworkflow.sdk.api.types.Task;
import org.openworkflow.sdk.api.types.TaskItem;

import java.util.Map;

class SignalCompiler implements StepCompiler {

    @Override
    public TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep, CompilationContext ctx) {
        String signalName = String.valueOf(stepValue);
        String taskName = "signal_send_" + signalName;
        ctx.recordSource(taskName);
        return new TaskItem(taskName, new Task().withSetTask(new SetTask()));
    }
}
