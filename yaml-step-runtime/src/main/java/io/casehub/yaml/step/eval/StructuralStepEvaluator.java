package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.orchestration.ExecutionScope;
import io.casehub.yaml.core.orchestration.OrcChannel;
import io.casehub.yaml.core.orchestration.OrcSignal;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.Result;
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
    private final ExecutionScope     scope;
    private final DecoratorChain     decoratorChain;
    private final io.casehub.yaml.core.resolver.ObjectVariableSource resultSource;
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.List<io.casehub.yaml.core.orchestration.OrcLatch>> stepLatches = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, QuorumTracker> quorumTrackers                                            = new java.util.concurrent.ConcurrentHashMap<>();


    public StructuralStepEvaluator(ConditionEvaluator conditionEvaluator) {
        this(conditionEvaluator, null);
    }

    public StructuralStepEvaluator(ConditionEvaluator conditionEvaluator, ExecutionScope scope) {
        this.conditionEvaluator = conditionEvaluator;
        this.scope              = scope;
        this.decoratorChain     = new DecoratorChain(conditionEvaluator,
                                                     io.casehub.yaml.core.runtime.SpeedMultiplier.identity(), scope);
        this.resultSource       = (scope != null) ? buildResultSource(scope.resultStore()) : null;
    }

    public Result evaluate(ResolvedStep step, VariableResolver resolver, StepRunner runner) {
        StepContext ctx = new StepContext(withResultScope(resolver));
        return evaluateInternal(step, ctx, runner);
    }

    private Result evaluateInternal(ResolvedStep step, StepContext ctx, StepRunner runner) {
        Map<String, Object> decorators = step.decorators();
        Result              result;
        if (decorators.isEmpty()) {
            result = dispatchStep(step, ctx, runner);
        } else {
            result = decoratorChain.apply(decorators, c -> dispatchStep(step, c, runner))
                                   .execute(ctx);
        }
        boolean isPolicyParallel = step instanceof ResolvedStep.ParallelStep p
                                   && p.advanceAfter() < p.steps().size();
        if (!isPolicyParallel) {
            recordResult(step.name(), result);
        }
        return result;
    }


    public void preRegisterLatches(java.util.List<ResolvedStep> steps) {
        if (scope == null) {return;}
        for (ResolvedStep step : steps) {
            switch (step) {
                case ResolvedStep.BarrierStep b -> {
                    io.casehub.yaml.core.orchestration.OrcLatch latch =
                            scope.latch("barrier:" + b.name(), b.awaitSteps().size());
                    for (String name : b.awaitSteps()) {
                        stepLatches.computeIfAbsent(name, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(latch);
                    }
                }
                case ResolvedStep.QuorumStep q -> {
                    io.casehub.yaml.core.orchestration.OrcLatch latch =
                            scope.latch("quorum:" + q.name(), q.required());
                    var tracker = new QuorumTracker(latch, q.required(), q.ofSteps().size(),
                                                    new AtomicInteger(), new AtomicInteger(), new java.util.concurrent.atomic.AtomicBoolean(false));
                    for (String name : q.ofSteps()) {
                        quorumTrackers.put(name, tracker);
                    }
                }
                case ResolvedStep.BlockStep b -> preRegisterLatches(b.steps());
                case ResolvedStep.ParallelStep p -> {
                    preRegisterLatches(p.steps());
                }
                case ResolvedStep.RaceStep r -> preRegisterLatches(r.branches());
                case ResolvedStep.TryCatchFinallyStep t -> {
                    preRegisterLatches(t.trySteps());
                    preRegisterLatches(t.catchSteps());
                    preRegisterLatches(t.finallySteps());
                }
                default -> {}
            }
        }
    }


    private Result dispatchStep(ResolvedStep step, StepContext ctx, StepRunner runner) {
        return switch (step) {
            case ResolvedStep.BlockStep b -> evaluateBlock(b, ctx, runner);
            case ResolvedStep.IfElseStep i -> evaluateIfElse(i, ctx, runner);
            case ResolvedStep.MatchStep m -> evaluateMatch(m, ctx, runner);
            case ResolvedStep.ParallelStep p -> evaluateParallel(p, ctx, runner);
            case ResolvedStep.TryCatchFinallyStep t -> evaluateTryCatchFinally(t, ctx, runner);
            case ResolvedStep.SelectStep s -> evaluateSelect(s, ctx, runner);
            case ResolvedStep.BarrierStep b -> evaluateBarrier(b);
            case ResolvedStep.QuorumStep q -> evaluateQuorum(q);
            case ResolvedStep.ControlStep c -> evaluateControl(c);
            case ResolvedStep.RaceStep r -> evaluateRace(r, ctx, runner);
            case ResolvedStep.PluginStep ps -> runner.run(ps, ctx.resolver());
            case ResolvedStep.InvokeStep is -> runner.run(is, ctx.resolver());
        };
    }

    private Result evaluateBlock(ResolvedStep.BlockStep block,
                                 StepContext ctx, StepRunner runner) {
        if (block.steps().isEmpty()) {
            return Result.of(Map.of());
        }
        Result last = Result.of(Map.of());
        for (ResolvedStep sub : block.steps()) {
            last = evaluateInternal(sub, ctx, runner);
            if (!last.isSuccess()) {
                return last;
            }
        }
        return last;
    }

    private Result evaluateIfElse(ResolvedStep.IfElseStep ifElse,
                                  StepContext ctx, StepRunner runner) {
        boolean condition;
        try {
            String resolved = ctx.resolver().resolveString(ifElse.condition(), "if-condition");
            condition = conditionEvaluator.evaluate(resolved);
        } catch (Exception e) {
            return Result.failed("Condition evaluation failed: " + e.getMessage());
        }

        List<ResolvedStep> branch = condition ? ifElse.thenSteps() : ifElse.elseSteps();
        if (branch.isEmpty()) {
            return Result.of(Map.of());
        }
        return evaluateBlock(new ResolvedStep.BlockStep(null, branch, Map.of()), ctx, runner);
    }

    private Result evaluateMatch(ResolvedStep.MatchStep match,
                                 StepContext ctx, StepRunner runner) {
        Object scrutineeValue = ctx.resolver().resolve(match.scrutinee());

        for (ResolvedMatchCase mc : match.cases()) {
            if (!mc.pattern().matches(scrutineeValue)) {
                continue;
            }
            if (mc.guard() != null) {
                try {
                    String resolvedGuard = ctx.resolver().resolveString(mc.guard(), "match-guard");
                    if (!conditionEvaluator.evaluate(resolvedGuard)) {
                        continue;
                    }
                } catch (Exception e) {
                    return Result.failed("Guard evaluation failed: " + e.getMessage());
                }
            }
            StepContext matchCtx = ctx.withResolver(pushMatchContext(ctx.resolver(), scrutineeValue));
            if (mc.steps().isEmpty()) {
                return Result.of(Map.of());
            }
            return evaluateBlock(
                    new ResolvedStep.BlockStep(null, mc.steps(), Map.of()), matchCtx, runner);
        }
        return Result.of(Map.of());
    }

    private Result evaluateTryCatchFinally(ResolvedStep.TryCatchFinallyStep tcf,
                                           StepContext ctx, StepRunner runner) {
        Result tryResult;
        try {
            tryResult = evaluateBlock(
                    new ResolvedStep.BlockStep(null, tcf.trySteps(), Map.of()), ctx, runner);
        } catch (Exception e) {
            tryResult = Result.failed(e.getMessage());
        }

        Result result = tryResult;
        if (!tryResult.isSuccess() && !tcf.catchSteps().isEmpty()) {
            String errorMessage = tryResult instanceof Result.Failure f ? f.message() : "unknown error";
            VariableResolver errorResolver = ctx.resolver().withObjectScope("error",
                                                                            name -> switch (name) {
                                                                                case "message" -> errorMessage;
                                                                                case "step" -> "try-block";
                                                                                default -> null;
                                                                            });
            try {
                result = evaluateBlock(
                        new ResolvedStep.BlockStep(null, tcf.catchSteps(), Map.of()), ctx.withResolver(errorResolver), runner);
            } catch (Exception e) {
                result = Result.failed("catch failed: " + e.getMessage());
            }
        }

        if (!tcf.finallySteps().isEmpty()) {
            Result finallyResult;
            try {
                finallyResult = evaluateBlock(
                        new ResolvedStep.BlockStep(null, tcf.finallySteps(), Map.of()), ctx, runner);
            } catch (Exception e) {
                finallyResult = Result.failed("finally failed: " + e.getMessage());
            }
            if (!finallyResult.isSuccess()) {
                return finallyResult;
            }
        }

        return result;
    }

    private Result evaluateSelect(ResolvedStep.SelectStep select,
                                  StepContext ctx, StepRunner runner) {
        if (select.branches().isEmpty()) {
            return Result.of(Map.of());
        }
        if (scope == null) {
            return Result.failed("'select' requires an ExecutionScope");
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
            return Result.failed("Select interrupted");
        }

        int winner = winnerIndex.get();
        if (winner < 0) {
            return Result.failed("No select branch completed");
        }

        ResolvedStep.SelectBranch winningBranch = select.branches().get(winner);
        if (winningBranch.steps().isEmpty()) {
            return Result.of(Map.of());
        }

        StepContext scoped  = ctx;
        Object      payload = winnerPayload.get();
        if (payload != null) {
            String prefix = winningBranch.type() == ResolvedStep.SelectBranchType.WAIT ? "signal" : "channel";
            scoped = ctx.withResolver(ScopeUtils.pushScope(ctx.resolver(), prefix, payload));
        }

        return evaluateBlock(
                new ResolvedStep.BlockStep(null, winningBranch.steps(), Map.of()), scoped, runner);
    }


    private VariableResolver pushMatchContext(VariableResolver resolver, Object value) {
        return ScopeUtils.pushScope(resolver, "match", value);
    }

    private Result evaluateParallel(ResolvedStep.ParallelStep parallel,
                                    StepContext ctx, StepRunner runner) {
        if (parallel.steps().isEmpty()) {
            return Result.of(Map.of());
        }

        if (parallel.advanceAfter() < parallel.steps().size()) {
            return evaluateParallelWithPolicy(parallel, ctx, runner);
        }

        int stepCount = parallel.steps().size();
        @SuppressWarnings("unchecked")
        Future<Result>[] futures = new Future[stepCount];

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < stepCount; i++) {
                ResolvedStep sub = parallel.steps().get(i);
                futures[i] = executor.submit(() -> evaluateInternal(sub, ctx, runner));
            }

            var results = new ArrayList<Result>(stepCount);
            for (Future<Result> f : futures) {
                try {
                    results.add(f.get());
                } catch (ExecutionException e) {
                    results.add(Result.failed(e.getCause().getMessage()));
                }
            }

            for (Result r : results) {
                if (!r.isSuccess()) {
                    return r;
                }
            }
            return results.get(results.size() - 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failed("Parallel execution interrupted");
        }
    }

    private Result evaluateParallelWithPolicy(ResolvedStep.ParallelStep parallel,
                                              StepContext ctx, StepRunner runner) {
        if (scope == null) {
            return Result.failed("parallel with advance-after requires an ExecutionScope");
        }
        int    totalSteps  = parallel.steps().size();
        int    required    = parallel.advanceAfter();
        String latchPrefix = "parallel:" + (parallel.name() != null ? parallel.name() : "anon-" + System.identityHashCode(parallel));

        io.casehub.yaml.core.orchestration.OrcLatch advancementLatch = scope.latch(latchPrefix + ":advance", required);
        io.casehub.yaml.core.orchestration.OrcLatch completionLatch  = scope.latch(latchPrefix + ":complete", totalSteps);

        var tracker = new ParallelCompletionTracker(
                advancementLatch, completionLatch, required, totalSteps,
                new AtomicInteger(), new AtomicInteger(),
                new java.util.concurrent.atomic.AtomicBoolean(), new AtomicReference<>());

        ExecutionScope branchScope = parallel.cancelRemaining() ? scope.childScope(latchPrefix + ":branches") : scope;

        for (ResolvedStep sub : parallel.steps()) {
            branchScope.spawn(
                    sub.name() != null ? sub.name() : "branch",
                    () -> {
                        Result result = evaluateInternal(sub, ctx, runner);
                        tracker.onStepComplete(result.isSuccess(), result);
                    });
        }

        try {
            advancementLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failed("Parallel advancement interrupted");
        }

        if (tracker.isUnreachable()) {
            if (parallel.cancelRemaining()) {
                branchScope.close();
            }
            return Result.failed("Advance threshold unreachable: required "
                                 + required + " successes from " + totalSteps + " steps, but "
                                 + tracker.failureCount().get() + " failed");
        }

        if (parallel.cancelRemaining()) {
            branchScope.close();
        }

        Result advancingResult = tracker.advancingResult().get();

        if (!parallel.cancelRemaining() && parallel.collectionTimeout() != null) {
            scope.spawn(latchPrefix + ":collection-deadline", () -> {
                try {
                    long millis = parallel.collectionTimeout().toMillis();
                    completionLatch.await(millis, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        if (!parallel.cancelRemaining()) {
            scope.spawn(latchPrefix + ":completion-watcher", () -> {
                try {
                    completionLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                recordResult(parallel.name(), advancingResult != null ? advancingResult : Result.of(Map.of()));
            });
        }

        return advancingResult != null ? advancingResult : Result.of(Map.of());
    }


    private Result evaluateBarrier(ResolvedStep.BarrierStep barrier) {
        if (scope == null) {
            return Result.failed("'barrier' requires an ExecutionScope");
        }
        io.casehub.yaml.core.orchestration.OrcLatch latch =
                scope.latch("barrier:" + barrier.name(), barrier.awaitSteps().size());
        try {
            if (barrier.timeout() != null) {
                boolean completed = latch.await(
                        barrier.timeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                if (!completed) {
                    return Result.failed("Barrier timed out after " + barrier.timeout());
                }
            } else {
                latch.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failed("Barrier interrupted");
        }
        return Result.of(Map.of());
    }

    private Result evaluateQuorum(ResolvedStep.QuorumStep quorum) {
        if (scope == null) {
            return Result.failed("'quorum' requires an ExecutionScope");
        }
        io.casehub.yaml.core.orchestration.OrcLatch latch =
                scope.latch("quorum:" + quorum.name(), quorum.required());
        try {
            if (quorum.timeout() != null) {
                boolean completed = latch.await(
                        quorum.timeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                if (!completed) {
                    return Result.failed("Quorum timed out — "
                                         + latch.getCount() + " of " + quorum.required() + " still needed");
                }
            } else {
                latch.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failed("Quorum interrupted");
        }
        QuorumTracker tracker = quorumTrackers.get(quorum.ofSteps().get(0));
        if (tracker != null && tracker.isUnreachable()) {
            return Result.failed("Quorum unreachable — "
                                 + tracker.failureCount().get() + " of " + quorum.ofSteps().size()
                                 + " steps failed, " + quorum.required() + " successes required");
        }
        return Result.of(Map.of());
    }

    private Result evaluateControl(ResolvedStep.ControlStep control) {
        if (scope == null) {
            return Result.failed("'pause'/'resume' requires an ExecutionScope");
        }
        io.casehub.yaml.core.orchestration.ConsumerHandle handle =
                scope.primitive(control.target(), io.casehub.yaml.core.orchestration.ConsumerHandle.class);
        if (handle == null) {
            return Result.failed("Consumer '" + control.target() + "' not found");
        }
        if (control.activate()) {
            handle.resume();
        } else {
            handle.pause();
        }
        return Result.of(Map.of());
    }

    @SuppressWarnings("unchecked")
    private Result evaluateRace(ResolvedStep.RaceStep race, StepContext ctx, StepRunner runner) {
        int         branchCount    = race.branches().size();
        var         winnerResult   = new AtomicReference<Result>();
        var         winnerIndex    = new AtomicInteger(-1);
        var         branchFailures = new AtomicReference[branchCount];
        Future<?>[] futures        = new Future<?>[branchCount];

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < branchCount; i++) {
                int idx = i;
                branchFailures[idx] = new AtomicReference<String>();
                ResolvedStep branch = race.branches().get(i);
                futures[i] = executor.submit(() -> {
                    Result result = evaluateInternal(branch, ctx, runner);
                    if (result.isSuccess() && winnerIndex.compareAndSet(-1, idx)) {
                        winnerResult.set(result);
                        for (int j = 0; j < branchCount; j++) {
                            if (j != idx && futures[j] != null) {
                                futures[j].cancel(true);
                            }
                        }
                    } else if (!result.isSuccess()) {
                        String msg = result instanceof Result.Failure f ? f.message() : "unknown";
                        branchFailures[idx].set("branch " + idx + ": " + msg);
                    }
                });
            }
            try {
                for (Future<?> f : futures) {
                    try {
                        f.get();
                    } catch (ExecutionException | java.util.concurrent.CancellationException e) {
                        // expected for cancelled branches
                    }
                }
            } finally {
                for (Future<?> f : futures) {
                    if (f != null) {
                        f.cancel(true);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failed("Race interrupted");
        }

        Result result = winnerResult.get();
        if (result != null) {
            return result;
        }

        var failures = new java.util.ArrayList<String>();
        for (var ref : branchFailures) {
            Object msg = ref.get();
            if (msg != null) {
                failures.add((String) msg);
            }
        }
        return Result.failed("All race branches failed: " + String.join("; ", failures));
    }


    private VariableResolver withResultScope(VariableResolver resolver) {
        return resultSource != null ? resolver.withObjectScope("result", resultSource) : resolver;
    }

    private void recordResult(String stepName, Result result) {
        if (stepName == null || scope == null) {return;}
        io.casehub.yaml.core.orchestration.StepResultStore store = scope.resultStore();
        if (result.isSuccess()) {
            store.recordSuccess(stepName, result.output());
        } else {
            String message = result instanceof Result.Failure f ? f.message() : "unknown error";
            store.recordFailure(stepName,
                                new io.casehub.yaml.core.error.RuntimeStepError.StepActionError(
                                        stepName, null, message,
                                        io.casehub.yaml.core.error.SourceLocation.UNKNOWN, null));
        }

        java.util.List<io.casehub.yaml.core.orchestration.OrcLatch> latches = stepLatches.get(stepName);
        if (latches != null) {
            for (io.casehub.yaml.core.orchestration.OrcLatch l : latches) {
                l.countDown();
            }
        }

        QuorumTracker tracker = quorumTrackers.get(stepName);
        if (tracker != null) {
            tracker.onStepComplete(result.isSuccess());
        }
    }

    private static io.casehub.yaml.core.resolver.ObjectVariableSource buildResultSource(
            io.casehub.yaml.core.orchestration.StepResultStore store) {
        return name -> {
            if (!store.hasCompleted(name)) {return null;}
            Map<String, Object>                          output = store.result(name);
            io.casehub.yaml.core.error.YamlError err = store.error(name);
            if (output == null && err == null) {return null;}
            if (output == null) {
                return Map.of("error", Map.of(
                        "message", err.summary(),
                        "category", err.category().name(),
                        "step", err.stepName() != null ? err.stepName() : ""));
            }
            if (err == null) {return output;}
            var composite = new java.util.HashMap<>(output);
            composite.put("error", Map.of(
                    "message", err.summary(),
                    "category", err.category().name(),
                    "step", err.stepName() != null ? err.stepName() : ""));
            return composite;
        };
    }

}
