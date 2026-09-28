package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.orchestration.DurationParser;
import io.casehub.yaml.core.orchestration.LoopDirective;
import io.casehub.yaml.core.orchestration.OrcChannel;
import io.casehub.yaml.core.orchestration.OrcSemaphore;
import io.casehub.yaml.core.orchestration.OrcSignal;
import io.casehub.yaml.core.orchestration.OrcStateMachine;
import io.casehub.yaml.core.orchestration.RetryDirective;
import io.casehub.yaml.core.orchestration.ScenarioScope;
import io.casehub.yaml.core.resolver.ObjectVariableSource;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.runtime.SpeedMultiplier;
import io.casehub.yaml.plugin.api.StepResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class DecoratorChain {

    private final ConditionEvaluator conditionEvaluator;
    private final SpeedMultiplier speedMultiplier;
    private final ScenarioScope scope;

    public DecoratorChain(ConditionEvaluator conditionEvaluator, SpeedMultiplier speedMultiplier) {
        this(conditionEvaluator, speedMultiplier, null);
    }

    public DecoratorChain(ConditionEvaluator conditionEvaluator, SpeedMultiplier speedMultiplier, ScenarioScope scope) {
        this.conditionEvaluator = conditionEvaluator;
        this.speedMultiplier = speedMultiplier;
        this.scope = scope;
    }

    public DecoratedExecution apply(Map<String, Object> decorators, DecoratedExecution inner) {
        if (decorators.isEmpty()) {
            return inner;
        }

        DecoratedExecution current = inner;
        current = wrapTransform(current, decorators);       // 13
        current = wrapTransition(current, decorators);      // 12
        current = wrapPostSignal(current, decorators);      // 11
        current = wrapDelay(current, decorators);           // 9
        current = wrapSemaphore(current, decorators);       // 8
        current = wrapRetry(current, decorators);           // 7
        current = wrapWait(current, decorators);            // 6
        current = wrapTimeout(current, decorators);         // 5
        current = wrapOnError(current, decorators);         // 4
        current = wrapLoop(current, decorators);            // 3
        current = wrapForEach(current, decorators);         // 2
        current = wrapWhen(current, decorators);            // 1

        return current;
    }

    private boolean resolveCondition(String condition, VariableResolver resolver, String context) {
        String resolved = resolver.resolveString(condition, context);
        return conditionEvaluator.evaluate(resolved);
    }


    private DecoratedExecution wrapWhen(DecoratedExecution inner, Map<String, Object> decorators) {
        Object whenVal = decorators.get("when");
        if (whenVal == null) {return inner;}

        String condition = String.valueOf(whenVal);
        return ctx -> {
            try {
                if (!resolveCondition(condition, ctx.resolver(), "when-guard")) {
                    return StepResult.of(Map.of());
                }
            } catch (Exception e) {
                return StepResult.failed("when guard failed: " + e.getMessage());
            }
            return inner.execute(ctx);
        };
    }

    private DecoratedExecution wrapLoop(DecoratedExecution inner, Map<String, Object> decorators) {
        Object loopVal = decorators.get("loop");
        if (loopVal == null) {return inner;}

        LoopDirective directive = LoopDirective.parse(loopVal);
        return ctx -> {
            StepResult last = StepResult.of(Map.of());
            int maxIterations = switch (directive) {
                case LoopDirective.Count c -> c.count();
                case LoopDirective.CountUntil cu -> cu.count();
                case LoopDirective.Until u -> 1000;
            };
            String untilCondition = switch (directive) {
                case LoopDirective.CountUntil cu -> cu.until();
                case LoopDirective.Until u -> u.until();
                default -> null;
            };

            for (int i = 0; i < maxIterations; i++) {
                last = inner.execute(ctx);
                if (!last.isSuccess()) {return last;}

                if (untilCondition != null) {
                    try {
                        if (resolveCondition(untilCondition, ctx.resolver(), "loop-until")) {
                            return last;
                        }
                    } catch (Exception e) {
                        return StepResult.failed("Loop until condition failed: " + e.getMessage());
                    }
                }
            }
            return last;
        };
    }

    private DecoratedExecution wrapOnError(DecoratedExecution inner, Map<String, Object> decorators) {
        Object onErrorVal = decorators.get("on-error");
        if (onErrorVal == null) {return inner;}

        String fallbackStep = String.valueOf(onErrorVal);
        return ctx -> {
            StepResult result;
            try {
                result = inner.execute(ctx);
            } catch (Exception e) {
                return StepResult.of(Map.of(
                                             "on-error.caught", e.getMessage(),
                                             "on-error.fallback", fallbackStep),
                                     Map.of("on-error.exception", e.getClass().getSimpleName()));
            }
            if (!result.isSuccess()) {
                String message = result instanceof StepResult.Failure f ? f.message() : "unknown";
                return StepResult.of(Map.of(
                                             "on-error.caught", message,
                                             "on-error.fallback", fallbackStep),
                                     Map.of("on-error.handled", true));
            }
            return result;
        };
    }

    private DecoratedExecution wrapTimeout(DecoratedExecution inner, Map<String, Object> decorators) {
        Object timeoutVal = decorators.get("timeout");
        if (timeoutVal == null) {return inner;}

        Duration timeout = DurationParser.parse(String.valueOf(timeoutVal));
        return ctx -> {
            double   speed      = speedMultiplier.currentSpeed();
            long     adjustedMs = Math.max(1, (long) (timeout.toMillis() / speed));
            Duration adjusted   = Duration.ofMillis(adjustedMs);

            StepContext deadlineCtx = ctx.withDeadline(adjusted);

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<StepResult> future = executor.submit(() -> inner.execute(deadlineCtx));
                try {
                    return future.get(adjustedMs, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    return StepResult.failed("Step timeout after " + timeout);
                } catch (java.util.concurrent.ExecutionException e) {
                    return StepResult.failed("Step execution failed: " + e.getCause().getMessage());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return StepResult.failed("Step interrupted during timeout");
            }
        };
    }

    private DecoratedExecution wrapRetry(DecoratedExecution inner, Map<String, Object> decorators) {
        Object retryVal = decorators.get("retry");
        if (retryVal == null) {return inner;}

        RetryDirective directive = RetryDirective.parse(retryVal);
        return ctx -> {
            int max = switch (directive) {
                case RetryDirective.Simple s -> s.max();
                case RetryDirective.Full f -> f.max();
            };
            Duration delay = switch (directive) {
                case RetryDirective.Full f -> f.delay();
                default -> Duration.ZERO;
            };
            String backoff = switch (directive) {
                case RetryDirective.Full f -> f.backoff();
                default -> "fixed";
            };

            StepResult last = null;
            for (int attempt = 0; attempt < max; attempt++) {
                try {
                    last = inner.execute(ctx);
                    if (last.isSuccess()) {return last;}
                } catch (Exception e) {
                    last = StepResult.failed(e.getMessage());
                }

                if (attempt < max - 1 && !delay.isZero()) {
                    long   delayMs    = computeDelay(delay, backoff, attempt);
                    double speed      = speedMultiplier.currentSpeed();
                    long   adjustedMs = Math.max(1, (long) (delayMs / speed));
                    try {
                        Thread.sleep(adjustedMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return StepResult.failed("Retry interrupted");
                    }
                }
            }
            return last;
        };
    }

    private static long computeDelay(Duration baseDelay, String backoff, int attempt) {
        long base = baseDelay.toMillis();
        return switch (backoff) {
            case "exponential" -> base * (1L << attempt);
            case "exponential-with-jitter" -> {
                long exp = base * (1L << attempt);
                yield exp + (long) (Math.random() * exp);
            }
            default -> base;
        };
    }

    private DecoratedExecution wrapDelay(DecoratedExecution inner, Map<String, Object> decorators) {
        Object delayVal = decorators.get("delay");
        if (delayVal == null) {return inner;}

        Duration delay = DurationParser.parse(String.valueOf(delayVal));
        return ctx -> {
            double speed      = speedMultiplier.currentSpeed();
            long   adjustedMs = Math.max(1, (long) (delay.toMillis() / speed));
            try {
                Thread.sleep(adjustedMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return StepResult.failed("Delay interrupted");
            }
            return inner.execute(ctx);
        };
    }

    private DecoratedExecution wrapTransform(DecoratedExecution inner, Map<String, Object> decorators) {
        Object transformVal = decorators.get("transform");
        if (transformVal == null) {return inner;}

        return ctx -> {
            StepResult result = inner.execute(ctx);
            if (!result.isSuccess()) {return result;}
            return result;
        };
    }

    @SuppressWarnings("unchecked")
    private DecoratedExecution wrapForEach(DecoratedExecution inner, Map<String, Object> decorators) {
        Object forEachVal = decorators.get("forEach");
        if (forEachVal == null) {return inner;}
        if (!(forEachVal instanceof Map<?, ?> forEachMap)) {
            return ctx -> StepResult.failed("forEach must be a map with 'in' and 'as' keys");
        }

        String inExpr = String.valueOf(forEachMap.get("in"));
        String as     = (String) forEachMap.get("as");
        if (as == null) {as = "item";}
        boolean parallel = Boolean.TRUE.equals(forEachMap.get("parallel"));
        String  asName   = as;

        return ctx -> {
            Object resolved = ctx.resolver().resolve(inExpr);
            if (!(resolved instanceof List<?> items)) {
                return StepResult.failed("forEach 'in' did not resolve to a list");
            }
            if (items.isEmpty()) {return StepResult.of(Map.of());}

            if (parallel) {
                return executeForEachParallel(items, asName, inner, ctx);
            }
            return executeForEachSequential(items, asName, inner, ctx);
        };
    }

    private StepResult executeForEachSequential(List<?> items, String as,
                                                DecoratedExecution inner, StepContext ctx) {
        StepResult last = StepResult.of(Map.of());
        for (int i = 0; i < items.size(); i++) {
            VariableResolver scoped = pushEachContext(ctx.resolver(), as, items.get(i), i);
            last = inner.execute(ctx.withResolver(scoped));
            if (!last.isSuccess()) {return last;}
        }
        return last;
    }

    private StepResult executeForEachParallel(List<?> items, String as,
                                              DecoratedExecution inner, StepContext ctx) {
        var futures = new ArrayList<Future<StepResult>>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < items.size(); i++) {
                int    idx  = i;
                Object item = items.get(i);
                futures.add(executor.submit(() -> {
                    VariableResolver scoped = pushEachContext(ctx.resolver(), as, item, idx);
                    return inner.execute(ctx.withResolver(scoped));
                }));
            }
            var results = new ArrayList<StepResult>(futures.size());
            for (var f : futures) {
                try {
                    results.add(f.get());
                } catch (java.util.concurrent.ExecutionException e) {
                    results.add(StepResult.failed(e.getCause().getMessage()));
                }
            }
            for (StepResult r : results) {
                if (!r.isSuccess()) {return r;}
            }
            return results.get(results.size() - 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StepResult.failed("forEach parallel interrupted");
        }
    }

    private VariableResolver pushEachContext(VariableResolver resolver, String as, Object item, int index) {
        ObjectVariableSource eachSource = name -> {
            if ("index".equals(name)) {return index;}
            if (as.equals(name)) {return item;}
            if (item instanceof Map<?, ?> map) {
                String prefix = as + ".";
                if (name.startsWith(prefix)) {return map.get(name.substring(prefix.length()));}
            }
            return null;
        };
        return resolver.withObjectScope("each", eachSource);
    }

    private DecoratedExecution wrapWait(DecoratedExecution inner, Map<String, Object> decorators) {
        Object waitVal = decorators.get("wait");
        if (waitVal == null) {return inner;}
        if (scope == null) {return ctx -> StepResult.failed("'wait' requires a ScenarioScope");}

        String signalName = String.valueOf(waitVal);
        return ctx -> {
            OrcSignal signal = scope.signal(signalName);
            try {
                var remaining = ctx.deadline().remainingTime();
                if (remaining.isPresent()) {
                    if (!signal.await(remaining.get().toMillis(), TimeUnit.MILLISECONDS)) {
                        return StepResult.failed(
                                "Wait for signal '" + signalName + "' exceeded deadline");
                    }
                } else {
                    signal.await();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return StepResult.failed("Wait for signal '" + signalName + "' interrupted");
            }
            VariableResolver scoped = ScopeUtils.pushScope(ctx.resolver(), "signal", signal.payload());
            return inner.execute(ctx.withResolver(scoped));
        };
    }

    @SuppressWarnings("unchecked")
    private DecoratedExecution wrapSemaphore(DecoratedExecution inner, Map<String, Object> decorators) {
        Object semVal   = decorators.get("semaphore");
        Object mutexVal = decorators.get("mutex");
        if (semVal == null && mutexVal == null) {return inner;}
        if (scope == null) {return ctx -> StepResult.failed("'semaphore'/'mutex' requires a ScenarioScope");}

        String name;
        int    permits;
        if (mutexVal != null) {
            name    = String.valueOf(mutexVal);
            permits = 1;
        } else if (semVal instanceof Map<?, ?> semMap) {
            name    = String.valueOf(semMap.get("name"));
            permits = semMap.containsKey("permits") ? ((Number) semMap.get("permits")).intValue() : 1;
        } else {
            name    = String.valueOf(semVal);
            permits = 1;
        }

        return ctx -> {
            OrcSemaphore semaphore = scope.semaphore(name, permits);
            try {
                var remaining = ctx.deadline().remainingTime();
                if (remaining.isPresent()) {
                    if (!semaphore.tryAcquire(remaining.get().toMillis(), TimeUnit.MILLISECONDS)) {
                        return StepResult.failed(
                                "Semaphore '" + name + "' acquire exceeded deadline");
                    }
                } else {
                    semaphore.acquire();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return StepResult.failed("Semaphore '" + name + "' acquire interrupted");
            }
            try {
                return inner.execute(ctx);
            } finally {
                semaphore.release();
            }
        };
    }

    private DecoratedExecution wrapPostSignal(DecoratedExecution inner, Map<String, Object> decorators) {
        Object signalVal  = decorators.get("signal");
        Object publishVal = decorators.get("publish");
        if (signalVal == null && publishVal == null) {return inner;}
        if (scope == null) {return ctx -> StepResult.failed("'signal'/'publish' requires a ScenarioScope");}

        return ctx -> {
            StepResult result = inner.execute(ctx);
            if (!result.isSuccess()) {return result;}

            if (signalVal != null) {
                OrcSignal signal = scope.signal(String.valueOf(signalVal));
                signal.signal(result.output());
            }
            if (publishVal != null) {
                applyPublish(publishVal, result, ctx.resolver());
            }
            return result;
        };
    }

    @SuppressWarnings("unchecked")
    private void applyPublish(Object publishVal, StepResult result, VariableResolver resolver) {
        if (!(publishVal instanceof Map<?, ?> pubMap)) return;
        String channelName = String.valueOf(pubMap.get("channel"));
        OrcChannel<Object> channel = scope.channel(channelName);
        Object data = pubMap.containsKey("data") ? resolver.resolve(pubMap.get("data")) : result.output();
        try {
            channel.send(data);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private DecoratedExecution wrapTransition(DecoratedExecution inner, Map<String, Object> decorators) {
        Object transVal = decorators.get("transition");
        if (transVal == null) {return inner;}
        if (scope == null) {return ctx -> StepResult.failed("'transition' requires a ScenarioScope");}
        if (!(transVal instanceof Map<?, ?> transMap)) {
            return ctx -> StepResult.failed("'transition' must be a map with 'machine' and 'event' keys");
        }

        String machineName = String.valueOf(transMap.get("machine"));
        String event       = String.valueOf(transMap.get("event"));
        String targetState = transMap.containsKey("to") ? String.valueOf(transMap.get("to")) : null;

        return ctx -> {
            StepResult result = inner.execute(ctx);
            if (!result.isSuccess()) {return result;}

            OrcStateMachine<? extends Enum<?>> machine = scope.primitive(machineName, OrcStateMachine.class);
            if (machine == null) {
                return StepResult.failed("State machine '" + machineName + "' not found");
            }
            Enum<?> currentState = machine.currentState();
            if (targetState != null) {
                fireDirectTransition(machine, currentState, targetState, event);
            } else {
                fireEventTransition(machine, currentState, event);
            }
            return result;
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void fireDirectTransition(OrcStateMachine machine, Enum currentState, String targetName, String event) {
        Object[] constants = currentState.getDeclaringClass().getEnumConstants();
        for (Object target : constants) {
            if (((Enum<?>) target).name().equalsIgnoreCase(targetName)) {
                machine.transition(currentState, (Enum) target, event);
                return;
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void fireEventTransition(OrcStateMachine machine, Enum currentState, String event) {
        Object[] constants = currentState.getDeclaringClass().getEnumConstants();
        for (Object target : constants) {
            if (machine.transition(currentState, (Enum) target, event)) {
                return;
            }
        }
    }

}
