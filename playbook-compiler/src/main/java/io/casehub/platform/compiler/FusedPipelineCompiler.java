package io.casehub.platform.compiler;

import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;

import java.util.Map;

class FusedPipelineCompiler implements StepCompiler {

    @Override
    public TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep,
                            CompilationContext ctx) {
        String name = ctx.generateName("fused-pipeline");
        return new TaskItem(name, new Task());
    }
}
