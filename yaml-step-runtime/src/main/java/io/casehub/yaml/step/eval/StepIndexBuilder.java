package io.casehub.yaml.step.eval;

import io.casehub.yaml.step.catalog.ResolvedStep;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StepIndexBuilder {

    private StepIndexBuilder() {}

    public static Map<String, ResolvedStep> build(List<ResolvedStep> steps) {
        Map<String, ResolvedStep> index = new LinkedHashMap<>();
        indexRecursive(steps, index);
        return Map.copyOf(index);
    }

    private static void indexRecursive(List<ResolvedStep> steps, Map<String, ResolvedStep> index) {
        for (ResolvedStep step : steps) {
            if (step.name() != null) {
                index.put(step.name(), step);
            }
            switch (step) {
                case ResolvedStep.BlockStep b -> indexRecursive(b.steps(), index);
                case ResolvedStep.ParallelStep p -> indexRecursive(p.steps(), index);
                case ResolvedStep.RaceStep r -> indexRecursive(r.branches(), index);
                case ResolvedStep.TryCatchFinallyStep t -> {
                    indexRecursive(t.trySteps(), index);
                    indexRecursive(t.catchSteps(), index);
                    indexRecursive(t.finallySteps(), index);
                }
                case ResolvedStep.IfElseStep i -> {
                    indexRecursive(i.thenSteps(), index);
                    indexRecursive(i.elseSteps(), index);
                }
                default -> {}
            }
        }
    }
}
