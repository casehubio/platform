package io.casehub.platform.compiler;

import io.serverlessworkflow.api.types.DoTask;
import io.serverlessworkflow.api.types.ForkTask;
import io.serverlessworkflow.api.types.ForkTaskConfiguration;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class RaceCompiler implements StepCompiler {

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
        var forkConfig = new ForkTaskConfiguration().withBranches(branches).withCompete(true);
        return new TaskItem(name, new Task().withForkTask(new ForkTask().withFork(forkConfig)));
    }
}
