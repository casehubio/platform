package io.casehub.platform.compiler;

import io.casehub.yaml.core.orchestration.DurationParser;
import io.casehub.yaml.core.playbook.PlaybookDocument;
import io.serverlessworkflow.api.types.DoTask;
import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.api.types.FlowDirective;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Retry;
import io.serverlessworkflow.api.types.RetryLimit;
import io.serverlessworkflow.api.types.RetryLimitAttempt;
import io.serverlessworkflow.api.types.RetryPolicy;
import io.serverlessworkflow.api.types.SetTask;
import io.serverlessworkflow.api.types.SwitchCase;
import io.serverlessworkflow.api.types.SwitchItem;
import io.serverlessworkflow.api.types.SwitchTask;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.TaskTimeout;
import io.serverlessworkflow.api.types.Timeout;
import io.serverlessworkflow.api.types.TimeoutAfter;
import io.serverlessworkflow.api.types.TryTask;
import io.serverlessworkflow.api.types.TryTaskCatch;
import io.serverlessworkflow.api.types.Workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlaybookCompiler {

    private final Map<String, StepCompiler> registry = new LinkedHashMap<>();

    public PlaybookCompiler() {
        register("log", (key, value, fullStep, ctx) -> {
            String name = ctx.generateName("set");
            return new TaskItem(name, new Task().withSetTask(new SetTask()));
        });
        register("block", new BlockCompiler());
        register("delay", new DelayCompiler());
        register("if", new IfElseCompiler());
        register("match", new MatchCompiler());
        register("try", new TryCatchCompiler());
        register("forEach", new ForEachCompiler());
        register("parallel", new ParallelCompiler());
        register("concurrent", new ConcurrentCompiler());
        register("race", new RaceCompiler());
        register("rest-request", new RestRequestCompiler());
        register("signal", new SignalCompiler());
        register("await", new AwaitCompiler());
    }

    public void register(String stepType, StepCompiler compiler) {
        registry.put(stepType, compiler);
    }

    private io.casehub.yaml.plugin.api.PluginRegistry pluginRegistry;

    public PlaybookCompiler(io.casehub.yaml.plugin.api.PluginRegistry pluginRegistry) {
        this();
        this.pluginRegistry = pluginRegistry;
    }

    public java.util.Optional<io.casehub.yaml.plugin.api.PluginRegistry> pluginRegistry() {
        return java.util.Optional.ofNullable(pluginRegistry);
    }


    public CompilationOutcome compile(PlaybookDocument doc) {
        String name = doc.hasPlaybookHeader() && doc.frontMatter().name() != null
                ? doc.frontMatter().name() : "playbook";
        return compile(name, doc.content());
    }

    public CompilationOutcome compile(Map<String, Object> content) {
        return compile("playbook", content);
    }

    @SuppressWarnings("unchecked")
    public CompilationOutcome compile(String name, Map<String, Object> content) {
        var sourceMap = new SourceMap.Builder();
        var coordination = new CoordinationSpec.Builder();
        var diagnostics = new ArrayList<CompilationDiagnostic>();

        if (content.containsKey("orchestration")) {
            parseOrchestration((Map<String, Object>) content.get("orchestration"), coordination);
        }

        List<?> steps = content.containsKey("steps") ? (List<?>) content.get("steps") : List.of();

        var rootCtx = new CompilationContext("/do", 0, sourceMap, coordination, diagnostics, this);
        var taskItems = new ArrayList<TaskItem>();
        for (int i = 0; i < steps.size(); i++) {
            @SuppressWarnings("unchecked")
            var step = (Map<String, Object>) steps.get(i);
            var childCtx = rootCtx.child("/do/" + i, i);
            TaskItem item = compileStep(step, childCtx);
            if (item != null) taskItems.add(item);
        }

        boolean hasErrors = diagnostics.stream()
                .anyMatch(d -> d.severity() == CompilationDiagnostic.Severity.ERROR);
        if (hasErrors) {
            return new CompilationOutcome.Failure(List.copyOf(diagnostics));
        }

        String safeName = sanitizeName(name);
        var doc = new Document().withDsl("1.0.0").withNamespace("casehub").withName(safeName).withVersion("0.1.0");
        var workflow = new Workflow(doc, taskItems);

        return new CompilationOutcome.Success(
                new CompilationResult(workflow, coordination.build(), sourceMap.build()));
    }

    TaskItem compileStep(Map<String, Object> step, CompilationContext ctx) {
        ResolvedStep resolved = resolveStructuralKeys(step);
        Map<String, Object> effectiveStep;
        DecoratorStack decorators;

        if (resolved != null) {
            effectiveStep = Map.of(resolved.stepKey, resolved.stepValue);
            decorators = DecoratorStack.extract(step).merge(resolved.additionalDecorators);
        } else {
            effectiveStep = DecoratorStack.strip(step);
            decorators = DecoratorStack.extract(step);
        }

        String stepKey = null;
        StepCompiler compiler = null;
        for (String key : effectiveStep.keySet()) {
            if (registry.containsKey(key)) {
                stepKey = key;
                compiler = registry.get(key);
                break;
            }
        }

        if (compiler == null) {
            ctx.addDiagnostic(CompilationDiagnostic.Severity.ERROR,
                    "Unknown step type: " + effectiveStep.keySet());
            return null;
        }

        TaskItem core = compiler.compile(stepKey, effectiveStep.get(stepKey), effectiveStep, ctx);
        if (core == null) return null;

        return applyDecorators(core, decorators, ctx);
    }

    private TaskItem applyDecorators(TaskItem core, DecoratorStack stack, CompilationContext ctx) {
        TaskItem result = core;

        if (stack.retry() != null) {
            String name = ctx.generateChildName("try", "retry");
            var retryPolicy = new RetryPolicy()
                    .withLimit(new RetryLimit()
                            .withAttempt(new RetryLimitAttempt()
                                    .withCount(stack.retry().max())));
            var retry = new Retry().withRetryPolicyDefinition(retryPolicy);
            var tryTask = new TryTask()
                    .withTry(List.of(result))
                    .withCatch(new TryTaskCatch().withRetry(retry).withDo(List.of()));
            result = new TaskItem(name, new Task().withTryTask(tryTask));
        }

        if (stack.timeout() != null) {
            var timeoutAfter = new TimeoutAfter().withDurationExpression(formatIsoDuration(stack.timeout()));
            var timeout = new Timeout().withAfter(timeoutAfter);
            var taskTimeout = new TaskTimeout().withTaskTimeoutDefinition(timeout);
            result.getTask().get();
            // TaskBase.withTimeout — access via the active task variant
            setTimeoutOnTask(result.getTask(), taskTimeout);
        }

        if (stack.when() != null) {
            String name = ctx.generateChildName("switch", "when");
            var thenDirective = new FlowDirective().withFlowDirectiveEnum(FlowDirectiveEnum.CONTINUE);
            var switchCase = new SwitchCase().withWhen(stack.when()).withThen(thenDirective);
            var guardItem = new SwitchItem("guard", switchCase);
            var doBlock = new DoTask().withDo(List.of(result));
            var doItem = new TaskItem(result.getName() + "_body", new Task().withDoTask(doBlock));
            var switchTask = new SwitchTask().withSwitch(List.of(guardItem));
            var switchTaskItem = new TaskItem(name, new Task().withSwitchTask(switchTask));
            result = switchTaskItem;
        }

        if (stack.mutex() != null) {
            ctx.addPrimitive(new CoordinationSpec.SemaphoreSpec(
                    stack.mutex(), 1, new CoordinationSpec.TaskMatcher.Exact(result.getName())));
        }

        return result;
    }

    private void setTimeoutOnTask(Task task, TaskTimeout timeout) {
        Object active = task.get();
        if (active instanceof io.serverlessworkflow.api.types.TaskBase tb) {
            tb.withTimeout(timeout);
        }
    }

    private String formatIsoDuration(java.time.Duration duration) {
        long millis = duration.toMillis();
        if (millis % 1000 == 0) {
            return "PT" + (millis / 1000) + "S";
        }
        return "PT" + millis + "S";
    }

    private record ResolvedStep(String stepKey, Object stepValue, DecoratorStack additionalDecorators) {}

    private ResolvedStep resolveStructuralKeys(Map<String, Object> step) {
        for (String key : step.keySet()) {
            if (key.startsWith("timeout ")) {
                return new ResolvedStep("block", step.get(key),
                        new DecoratorStack(null, DurationParser.parse(key.substring(8).trim()), null, null, null));
            }
            if (key.startsWith("retry ")) {
                return new ResolvedStep("block", step.get(key),
                        new DecoratorStack(null, null,
                                new DecoratorStack.RetrySpec(Integer.parseInt(key.substring(6).trim())),
                                null, null));
            }
            if (key.startsWith("when ")) {
                return new ResolvedStep("block", step.get(key),
                        new DecoratorStack(key.substring(5), null, null, null, null));
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void parseOrchestration(Map<String, Object> orch, CoordinationSpec.Builder coordination) {
        if (orch.containsKey("barriers")) {
            var barriers = (Map<String, Object>) orch.get("barriers");
            for (var entry : barriers.entrySet()) {
                var config = (Map<String, Object>) entry.getValue();
                int count = ((Number) config.get("count")).intValue();
                coordination.add(new CoordinationSpec.BarrierSpec(
                        entry.getKey(), count,
                        new CoordinationSpec.TaskMatcher.Prefix("barrier_" + entry.getKey())));
            }
        }
        if (orch.containsKey("signals")) {
            var signals = (List<String>) orch.get("signals");
            for (String signal : signals) {
                coordination.add(new CoordinationSpec.SignalSpec(signal,
                        new CoordinationSpec.TaskMatcher.Prefix("signal_send_" + signal),
                        new CoordinationSpec.TaskMatcher.Prefix("signal_recv_" + signal)));
            }
        }
    }

    private static String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9-]", "-").replaceAll("^-+|-+$", "")
                .substring(0, Math.min(name.length(), 63));
    }
}
