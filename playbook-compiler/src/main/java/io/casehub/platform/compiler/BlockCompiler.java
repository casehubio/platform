package io.casehub.platform.compiler;

import org.openworkflow.sdk.api.types.DoTask;
import org.openworkflow.sdk.api.types.Task;
import org.openworkflow.sdk.api.types.TaskItem;

import java.util.List;
import java.util.Map;

public class BlockCompiler implements StepCompiler {

    @Override
    public TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep, CompilationContext ctx) {
        String name = ctx.generateName("do");
        List<?> steps = (List<?>) stepValue;
        var children = ctx.compileSteps(steps);
        return new TaskItem(name, new Task().withDoTask(new DoTask().withDo(children)));
    }
}
