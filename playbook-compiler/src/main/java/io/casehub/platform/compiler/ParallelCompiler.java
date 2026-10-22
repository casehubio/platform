package io.casehub.platform.compiler;

import org.openworkflow.sdk.api.types.DoTask;
import org.openworkflow.sdk.api.types.ForkTask;
import org.openworkflow.sdk.api.types.ForkTaskConfiguration;
import org.openworkflow.sdk.api.types.Task;
import org.openworkflow.sdk.api.types.TaskItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ParallelCompiler implements StepCompiler {

    @Override
    @SuppressWarnings("unchecked")
    public TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep, CompilationContext ctx) {
        String name = ctx.generateName("fork");
        List<?> steps = (List<?>) stepValue;
        var branches = new ArrayList<TaskItem>();
        for (int i = 0; i < steps.size(); i++) {
            var step = (Map<String, Object>) steps.get(i);
            var branchItems = ctx.compileSteps(List.of(step));
            String branchName = name + "_branch_" + i;
            branches.add(new TaskItem(branchName, new Task().withDoTask(new DoTask().withDo(branchItems))));
        }
        var forkConfig = new ForkTaskConfiguration().withBranches(branches).withCompete(false);
        return new TaskItem(name, new Task().withForkTask(new ForkTask().withFork(forkConfig)));
    }
}
