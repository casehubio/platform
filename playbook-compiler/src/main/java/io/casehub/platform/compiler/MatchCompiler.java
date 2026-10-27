package io.casehub.platform.compiler;

import io.serverlessworkflow.api.types.FlowDirective;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.SwitchCase;
import io.serverlessworkflow.api.types.SwitchItem;
import io.serverlessworkflow.api.types.SwitchTask;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MatchCompiler implements StepCompiler {

    @Override
    @SuppressWarnings("unchecked")
    public TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep, CompilationContext ctx) {
        String name = ctx.generateName("switch");
        String matchExpr = String.valueOf(stepValue);

        var switchItems = new ArrayList<SwitchItem>();
        List<?> cases = (List<?>) fullStep.get("cases");
        if (cases != null) {
            for (int i = 0; i < cases.size(); i++) {
                var caseMap = (Map<String, Object>) cases.get(i);
                if (caseMap.containsKey("pattern")) {
                    String pattern = String.valueOf(caseMap.get("pattern"));
                    var switchCase = new SwitchCase()
                            .withWhen("${ " + matchExpr + " == \"" + pattern + "\" }")
                            .withThen(new FlowDirective().withFlowDirectiveEnum(FlowDirectiveEnum.CONTINUE));
                    switchItems.add(new SwitchItem("case_" + i, switchCase));
                } else if (caseMap.containsKey("default")) {
                    var switchCase = new SwitchCase()
                            .withThen(new FlowDirective().withFlowDirectiveEnum(FlowDirectiveEnum.CONTINUE));
                    switchItems.add(new SwitchItem("default", switchCase));
                }
            }
        }

        return new TaskItem(name, new Task().withSwitchTask(new SwitchTask().withSwitch(switchItems)));
    }
}
