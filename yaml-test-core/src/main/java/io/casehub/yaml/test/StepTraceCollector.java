package io.casehub.yaml.test;

import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.ResolvedStep;
import io.casehub.yaml.step.eval.StepExecutionListener;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public final class StepTraceCollector implements StepExecutionListener {

    private final Deque<Frame> stack = new LinkedList<>();
    private final List<StepTraceNode> rootChildren = new ArrayList<>();
    private final SimulationRuntime simulationRuntime;

    public StepTraceCollector() {
        this(null);
    }

    public StepTraceCollector(SimulationRuntime simulationRuntime) {
        this.simulationRuntime = simulationRuntime;
    }

    private record Frame(int index, String name, String type, long startNanos,
                         List<StepTraceNode> children) {}

    @Override
    public void beforeStep(ResolvedStep step) {
        enterStep(0, step.name(), PlaybookRunnerCore.resolveStepType(step));
    }

    @Override
    public void afterStep(ResolvedStep step, Result result, Map<String, Object> detail) {
        String action = step instanceof ResolvedStep.PluginStep ps ? ps.actionName() : null;
        StepOutcome outcome = result.isSuccess() ? StepOutcome.EXECUTED : StepOutcome.FAILED;
        Map<String, Object> enrichedDetail = detail;

        if (result.isSuccess() && step instanceof ResolvedStep.PluginStep ps
                && simulationRuntime != null) {
            var strategy = simulationRuntime.strategyFor(ps.actionName());
            if (strategy.isPresent()) {
                outcome = StepOutcome.SIMULATED;
                enrichedDetail = new LinkedHashMap<>(detail);
                enrichedDetail.put("strategy", strategy.get().getClass().getSimpleName()
                        .replace("Strategy", "").toLowerCase());
            }
        }

        exitStep(action, outcome, enrichedDetail);
    }

    public synchronized void enterStep(int index, String name, String type) {
        stack.push(new Frame(index, name, type, System.nanoTime(), new ArrayList<>()));
    }

    public synchronized void exitStep(String action, StepOutcome outcome, Map<String, Object> detail) {
        Frame frame = stack.pop();
        long durationMs = (System.nanoTime() - frame.startNanos()) / 1_000_000;
        var node = new StepTraceNode(frame.index(), frame.name(), frame.type(),
                action, outcome, durationMs, detail, frame.children());

        if (stack.isEmpty()) {
            rootChildren.add(node);
        } else {
            stack.peek().children().add(node);
        }
    }

    public List<StepTraceNode> rootChildren() {
        return List.copyOf(rootChildren);
    }

    public int depth() {
        return stack.size();
    }
}
