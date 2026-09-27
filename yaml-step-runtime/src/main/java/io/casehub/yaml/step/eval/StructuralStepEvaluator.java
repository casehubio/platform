package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.orchestration.OrcChannel;
import io.casehub.yaml.core.orchestration.OrcSignal;
import io.casehub.yaml.core.orchestration.ScenarioScope;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class StructuralStepEvaluator {

    private final ConditionEvaluator conditionEvaluator;
    private final ScenarioScope scope;
    private final DecoratorChain decoratorChain;
    private final io.casehub.yaml.core.resolver.ObjectVariableSource resultSource;


    public StructuralStepEvaluator(ConditionEvaluator conditionEvaluator) {
        this(conditionEvaluator, null);
    }

    public StructuralStepEvaluator(ConditionEvaluator conditionEvaluator, ScenarioScope scope) {
        this.conditionEvaluator = conditionEvaluator;
        this.scope              = scope;
        this.decoratorChain     = new DecoratorChain(conditionEvaluator,
                                                     io.casehub.yaml.core.runtime.SpeedMultiplier.identity(), scope);
        this.resultSource       = (scope != null) ? buildResultSource(scope.resultStore()) : null;
    }

    public StepResult evaluate(ResolvedStep step, VariableResolver resolver, StepRunner runner) {
        VariableResolver    effective  = withResultScope(resolver);
        Map<String, Object> decorators = step.decorators();
        StepResult          result;
        if (decorators.isEmpty()) {
            result = dispatchStep(step, effective, runner);
        } else {
            result = decoratorChain.apply(decorators, r -> dispatchStep(step, r, runner))
                                   .execute(effective);
        }
        recordResult(step.name(), result);
        return result;
    }

    private StepResult dispatchStep(ResolvedStep step, VariableResolver resolver, StepRunner runner) {
        return switch (step) {
            case ResolvedStep.BlockStep b -> evaluateBlock(b, resolver, runner);
            case ResolvedStep.IfElseStep i -> evaluateIfElse(i, resolver, runner);
            case ResolvedStep.MatchStep m -> evaluateMatch(m, resolver, runner);
            case ResolvedStep.ParallelStep p -> evaluateParallel(p, resolver, runner);
            case ResolvedStep.TryCatchFinallyStep t -> evaluateTryCatchFinally(t, resolver, runner);
            case ResolvedStep.SelectStep s -> evaluateSelect(s, resolver, runner);
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
        return evaluateBlock(new ResolvedStep.BlockStep(null, branch, Map.of()), resolver, runner);
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
                    new ResolvedStep.BlockStep(null, mc.steps(), Map.of()), matchResolver, runner);
        }
        return StepResult.of(Map.of());
    }

    private StepResult evaluateTryCatchFinally(ResolvedStep.TryCatchFinallyStep tcf,
                                               VariableResolver resolver, StepRunner runner) {
        StepResult tryResult;
        try {
            tryResult = evaluateBlock(
                    new ResolvedStep.BlockStep(null, tcf.trySteps(), Map.of()), resolver, runner);
        } catch (Exception e) {
            tryResult = StepResult.failed(e.getMessage());
        }

        StepResult result = tryResult;
        if (!tryResult.isSuccess() && !tcf.catchSteps().isEmpty()) {
            String errorMessage = tryResult instanceof StepResult.Failure f ? f.message() : "unknown error";
            VariableResolver errorResolver = resolver.withObjectScope("error",
                                                                      name -> switch (name) {
                                                                          case "message" -> errorMessage;
                                                                          case "step" -> "try-block";
                                                                          default -> null;
                                                                      });
            try {
                result = evaluateBlock(
                        new ResolvedStep.BlockStep(null, tcf.catchSteps(), Map.of()), errorResolver, runner);
            } catch (Exception e) {
                result = StepResult.failed("catch failed: " + e.getMessage());
            }
        }

        if (!tcf.finallySteps().isEmpty()) {
            StepResult finallyResult;
            try {
                finallyResult = evaluateBlock(
                        new ResolvedStep.BlockStep(null, tcf.finallySteps(), Map.of()), resolver, runner);
            } catch (Exception e) {
                finallyResult = StepResult.failed("finally failed: " + e.getMessage());
            }
            if (!finallyResult.isSuccess()) {
                return finallyResult;
            }
        }

        return result;
    }

    private StepResult evaluateSelect(ResolvedStep.SelectStep select,
                                      VariableResolver resolver, StepRunner runner) {
        if (select.branches().isEmpty()) {
            return StepResult.of(Map.of());
        }
        if (scope == null) {
            return StepResult.failed("'select' requires a ScenarioScope");
        }

        var winnerIndex   = new AtomicInteger(-1);
        var winnerPayload = new AtomicReference<Object>();
        int branchCount   = select.branches().size();
        @SuppressWarnings("unchecked")
        Future<Object>[] futureSlots = new Future[branchCount];

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < branchCount; i++) {
                int                       branchIdx = i;
                ResolvedStep.SelectBranch branch    = select.branches().get(i);
                futureSlots[i] = executor.submit(() -> {
                    try {
                        Object payload;
                        if (branch.type() == ResolvedStep.SelectBranchType.WAIT) {
                            OrcSignal signal = scope.signal(branch.name());
                            signal.await();
                            payload = signal.payload();
                        } else {
                            OrcChannel<Object> channel = scope.channel(branch.name());
                            payload = channel.receive();
                        }
                        if (winnerIndex.compareAndSet(-1, branchIdx)) {
                            winnerPayload.set(payload);
                            for (int j = 0; j < branchCount; j++) {
                                if (j != branchIdx) {
                                    Future<?> other = futureSlots[j];
                                    if (other != null) {other.cancel(true);}
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            }

            for (Future<Object> f : futureSlots) {
                try {
                    f.get();
                } catch (ExecutionException | java.util.concurrent.CancellationException e) {
                    // expected for cancelled branches
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StepResult.failed("Select interrupted");
        }

        int winner = winnerIndex.get();
        if (winner < 0) {
            return StepResult.failed("No select branch completed");
        }

        ResolvedStep.SelectBranch winningBranch = select.branches().get(winner);
        if (winningBranch.steps().isEmpty()) {
            return StepResult.of(Map.of());
        }

        VariableResolver scoped = resolver;
        Object payload = winnerPayload.get();
        if (payload != null) {
            String prefix = winningBranch.type() == ResolvedStep.SelectBranchType.WAIT ? "signal" : "channel";
            scoped = ScopeUtils.pushScope(resolver, prefix, payload);
        }

        return evaluateBlock(
                new ResolvedStep.BlockStep(null, winningBranch.steps(), Map.of()), scoped, runner);
    }


    private VariableResolver pushMatchContext(VariableResolver resolver, Object value) {
        return ScopeUtils.pushScope(resolver, "match", value);
    }

    private StepResult evaluateParallel(ResolvedStep.ParallelStep parallel,
                                        VariableResolver resolver, StepRunner runner) {
        if (parallel.steps().isEmpty()) {
            return StepResult.of(Map.of());
        }

        int stepCount = parallel.steps().size();
        @SuppressWarnings("unchecked")
        Future<StepResult>[] futures = new Future[stepCount];

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < stepCount; i++) {
                ResolvedStep sub = parallel.steps().get(i);
                futures[i] = executor.submit(() -> evaluate(sub, resolver, runner));
            }

            var results = new ArrayList<StepResult>(stepCount);
            for (Future<StepResult> f : futures) {
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

    private VariableResolver withResultScope(VariableResolver resolver) {
        return resultSource != null ? resolver.withObjectScope("result", resultSource) : resolver;
    }

    private void recordResult(String stepName, StepResult result) {
        if (stepName == null || scope == null) {return;}
        io.casehub.yaml.core.orchestration.StepResultStore store = scope.resultStore();
        if (result.isSuccess()) {
            store.recordSuccess(stepName, result.output());
        } else {
            String message = result instanceof StepResult.Failure f ? f.message() : "unknown error";
            store.recordFailure(stepName,
                                new io.casehub.yaml.core.orchestration.StepError(message, null, null));
        }
    }

    private static io.casehub.yaml.core.resolver.ObjectVariableSource buildResultSource(
            io.casehub.yaml.core.orchestration.StepResultStore store) {
        return name -> {
            if (!store.hasCompleted(name)) {return null;}
            Map<String, Object>                          output = store.result(name);
            io.casehub.yaml.core.orchestration.StepError err    = store.error(name);
            if (output == null && err == null) {return null;}
            if (output == null) {
                return Map.of("error", Map.of(
                        "message", err.message() != null ? err.message() : "",
                        "exceptionClass", err.exceptionClass() != null ? err.exceptionClass() : "",
                        "stackTrace", err.stackTrace() != null ? err.stackTrace() : ""));
            }
            if (err == null) {return output;}
            var composite = new java.util.HashMap<>(output);
            composite.put("error", Map.of(
                    "message", err.message() != null ? err.message() : "",
                    "exceptionClass", err.exceptionClass() != null ? err.exceptionClass() : "",
                    "stackTrace", err.stackTrace() != null ? err.stackTrace() : ""));
            return composite;
        };
    }

}
