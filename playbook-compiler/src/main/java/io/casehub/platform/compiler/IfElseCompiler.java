package io.casehub.platform.compiler;

import org.openworkflow.sdk.api.types.DoTask;
import org.openworkflow.sdk.api.types.FlowDirective;
import org.openworkflow.sdk.api.types.FlowDirectiveEnum;
import org.openworkflow.sdk.api.types.SwitchCase;
import org.openworkflow.sdk.api.types.SwitchItem;
import org.openworkflow.sdk.api.types.SwitchTask;
import org.openworkflow.sdk.api.types.Task;
import org.openworkflow.sdk.api.types.TaskItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IfElseCompiler implements StepCompiler {

    @Override
    @SuppressWarnings("unchecked")
    public TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep, CompilationContext ctx) {
        String name = ctx.generateName("switch");
        String condition = String.valueOf(stepValue);

        var switchItems = new ArrayList<SwitchItem>();

        if (fullStep.containsKey("then")) {
            List<?> thenSteps = (List<?>) fullStep.get("then");
            var thenItems = ctx.compileSteps(thenSteps);
            var thenBlock = new TaskItem(name + "_then", new Task().withDoTask(new DoTask().withDo(thenItems)));

            var thenCase = new SwitchCase().withWhen(condition)
                    .withThen(new FlowDirective().withFlowDirectiveEnum(FlowDirectiveEnum.CONTINUE));
            switchItems.add(new SwitchItem("then", thenCase));
        }

        if (fullStep.containsKey("else")) {
            List<?> elseSteps = (List<?>) fullStep.get("else");
            var elseItems = ctx.compileSteps(elseSteps);
            var elseBlock = new TaskItem(name + "_else", new Task().withDoTask(new DoTask().withDo(elseItems)));

            var elseCase = new SwitchCase()
                    .withThen(new FlowDirective().withFlowDirectiveEnum(FlowDirectiveEnum.CONTINUE));
            switchItems.add(new SwitchItem("else", elseCase));
        }

        return new TaskItem(name, new Task().withSwitchTask(new SwitchTask().withSwitch(switchItems)));
    }
}
