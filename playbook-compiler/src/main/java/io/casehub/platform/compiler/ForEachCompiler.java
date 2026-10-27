package io.casehub.platform.compiler;

import io.serverlessworkflow.api.types.ForIn;
import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.ForTaskConfiguration;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;

import java.util.List;
import java.util.Map;

class ForEachCompiler implements StepCompiler {

    @Override
    @SuppressWarnings("unchecked")
    public TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep, CompilationContext ctx) {
        String name = ctx.generateName("for");

        if (stepValue instanceof Map<?, ?> config) {
            String each = config.containsKey("as") ? String.valueOf(config.get("as")) : "item";
            String in = String.valueOf(config.get("in"));
            var forIn = new ForIn().withForInExpression(in);
            var forConfig = new ForTaskConfiguration().withEach(each).withIn(forIn);
            var forTask = new ForTask().withFor(forConfig).withDo(List.of());
            return new TaskItem(name, new Task().withForTask(forTask));
        }

        return new TaskItem(name, new Task().withForTask(new ForTask().withDo(List.of())));
    }
}
