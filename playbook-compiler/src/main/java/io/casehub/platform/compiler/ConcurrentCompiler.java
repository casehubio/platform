package io.casehub.platform.compiler;

import org.openworkflow.sdk.api.types.DoTask;
import org.openworkflow.sdk.api.types.ForkTask;
import org.openworkflow.sdk.api.types.ForkTaskConfiguration;
import org.openworkflow.sdk.api.types.Task;
import org.openworkflow.sdk.api.types.TaskItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ConcurrentCompiler implements StepCompiler {

    @Override
    @SuppressWarnings("unchecked")
    public TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep, CompilationContext ctx) {
        String name = ctx.generateName("fork");
        Map<String, Object> namedBranches = (Map<String, Object>) stepValue;
        var branches = new ArrayList<TaskItem>();
        for (var entry : namedBranches.entrySet()) {
            List<?> branchSteps = (List<?>) entry.getValue();
            var items = ctx.compileSteps(branchSteps);
            String branchName = name + "_" + entry.getKey();
            branches.add(new TaskItem(branchName, new Task().withDoTask(new DoTask().withDo(items))));
        }
        var forkConfig = new ForkTaskConfiguration().withBranches(branches).withCompete(false);
        return new TaskItem(name, new Task().withForkTask(new ForkTask().withFork(forkConfig)));
    }
}
