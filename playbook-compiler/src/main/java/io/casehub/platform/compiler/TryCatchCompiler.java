package io.casehub.platform.compiler;

import org.openworkflow.sdk.api.types.Task;
import org.openworkflow.sdk.api.types.TaskItem;
import org.openworkflow.sdk.api.types.TryTask;
import org.openworkflow.sdk.api.types.TryTaskCatch;

import java.util.List;
import java.util.Map;

class TryCatchCompiler implements StepCompiler {

    @Override
    @SuppressWarnings("unchecked")
    public TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep, CompilationContext ctx) {
        String name = ctx.generateName("try");
        List<?> trySteps = (List<?>) stepValue;
        var tryItems = ctx.compileSteps(trySteps);

        var catchBlock = new TryTaskCatch();
        if (fullStep.containsKey("catch")) {
            List<?> catchSteps = (List<?>) fullStep.get("catch");
            catchBlock.withDo(ctx.compileSteps(catchSteps));
        } else {
            catchBlock.withDo(List.of());
        }

        var tryTask = new TryTask().withTry(tryItems).withCatch(catchBlock);
        return new TaskItem(name, new Task().withTryTask(tryTask));
    }
}
