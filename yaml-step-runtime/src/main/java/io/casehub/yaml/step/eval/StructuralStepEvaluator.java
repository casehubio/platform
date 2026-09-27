package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.resolver.ObjectVariableSource;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.StepResult;
import io.casehub.yaml.step.catalog.ResolvedMatchCase;
import io.casehub.yaml.step.catalog.ResolvedStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class StructuralStepEvaluator {

    private final ConditionEvaluator conditionEvaluator;

    public StructuralStepEvaluator(ConditionEvaluator conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator;
    }

    public StepResult evaluate(ResolvedStep step, VariableResolver resolver, StepRunner runner) {
        return switch (step) {
            case ResolvedStep.BlockStep b -> evaluateBlock(b, resolver, runner);
            case ResolvedStep.IfElseStep i -> evaluateIfElse(i, resolver, runner);
            case ResolvedStep.MatchStep m -> evaluateMatch(m, resolver, runner);
            case ResolvedStep.ParallelStep p -> evaluateParallel(p, resolver, runner);
            case ResolvedStep.PluginStep ps -> runner.run(ps, resolver);
            case ResolvedStep.InvokeStep is -> runner.run(is, resolver);
        };
    }

    private StepResult evaluateBlock(ResolvedStep.BlockStep block,
                                     VariableResolver resolver, StepRunner runner) {
        if (block.steps().isEmpty()) {
            return StepResult.of(Map.of());
        }
        StepResult last = StepResult.of(Map.of());
        for (ResolvedStep sub : block.steps()) {
            last = evaluate(sub, resolver, runner);
            if (!last.isSuccess()) {
                return last;
            }
        }
        return last;
    }

    private StepResult evaluateIfElse(ResolvedStep.IfElseStep ifElse,
                                      VariableResolver resolver, StepRunner runner) {
        boolean condition;
        try {
            String resolved = resolver.resolveString(ifElse.condition(), "if-condition");
            condition = conditionEvaluator.evaluate(resolved);
        } catch (Exception e) {
            return StepResult.failed("Condition evaluation failed: " + e.getMessage());
        }

        List<ResolvedStep> branch = condition ? ifElse.thenSteps() : ifElse.elseSteps();
        if (branch.isEmpty()) {
            return StepResult.of(Map.of());
        }
        return evaluateBlock(new ResolvedStep.BlockStep(branch, Map.of()), resolver, runner);
    }

    private StepResult evaluateMatch(ResolvedStep.MatchStep match,
                                     VariableResolver resolver, StepRunner runner) {
        Object scrutineeValue = resolver.resolve(match.scrutinee());

        for (ResolvedMatchCase mc : match.cases()) {
            if (!mc.pattern().matches(scrutineeValue)) {
                continue;
            }
            if (mc.guard() != null) {
                try {
                    String resolvedGuard = resolver.resolveString(mc.guard(), "match-guard");
                    if (!conditionEvaluator.evaluate(resolvedGuard)) {
                        continue;
                    }
                } catch (Exception e) {
                    return StepResult.failed("Guard evaluation failed: " + e.getMessage());
                }
            }
            VariableResolver matchResolver = pushMatchContext(resolver, scrutineeValue);
            if (mc.steps().isEmpty()) {
                return StepResult.of(Map.of());
            }
            return evaluateBlock(
                    new ResolvedStep.BlockStep(mc.steps(), Map.of()), matchResolver, runner);
        }
        return StepResult.of(Map.of());
    }

    private VariableResolver pushMatchContext(VariableResolver resolver, Object value) {
        ObjectVariableSource matchSource;
        if (value instanceof Map<?, ?> map) {
            matchSource = name -> {
                if (name.isEmpty()) return value;
                return map.get(name);
            };
        } else {
            matchSource = name -> value;
        }
        return resolver.withObjectScope("match", matchSource);
    }

    private StepResult evaluateParallel(ResolvedStep.ParallelStep parallel,
                                        VariableResolver resolver, StepRunner runner) {
        if (parallel.steps().isEmpty()) {
            return StepResult.of(Map.of());
        }

        var futures = new ArrayList<Future<StepResult>>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (ResolvedStep sub : parallel.steps()) {
                futures.add(executor.submit(() -> evaluate(sub, resolver, runner)));
            }

            var results = new ArrayList<StepResult>(futures.size());
            for (var f : futures) {
                try {
                    results.add(f.get());
                } catch (ExecutionException e) {
                    results.add(StepResult.failed(e.getCause().getMessage()));
                }
            }

            for (StepResult r : results) {
                if (!r.isSuccess()) {
                    return r;
                }
            }
            return results.get(results.size() - 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StepResult.failed("Parallel execution interrupted");
        }
    }
}
