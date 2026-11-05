package io.casehub.yaml.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.platform.simulation.MapSimulationConfig;
import io.casehub.platform.simulation.SimulationOverlay;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.yaml.core.playbook.PlaybookDocument;
import io.casehub.yaml.core.playbook.PlaybookFrontMatter;
import io.casehub.yaml.core.playbook.PlaybookVariableParser;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.plugin.api.ServiceRegistry;
import io.casehub.yaml.step.catalog.ResolvedStep;
import io.casehub.yaml.step.catalog.Walker;
import io.casehub.yaml.step.eval.CatalogStepRunner;
import io.casehub.yaml.step.eval.StructuralStepEvaluator;
import io.casehub.yaml.step.plugin.EventSink;
import io.casehub.yaml.step.statemachine.PlaybookParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlaybookRunnerCore {

    private final PluginRegistry pluginRegistry;
    private final ServiceRegistry serviceRegistry;
    private final Path resourceRoot;
    private final SimulationRuntime simulationRuntime;

    public PlaybookRunnerCore(PluginRegistry pluginRegistry,
                              ServiceRegistry serviceRegistry,
                              Path resourceRoot,
                              SimulationRuntime simulationRuntime) {
        this.pluginRegistry = pluginRegistry;
        this.serviceRegistry = serviceRegistry;
        this.resourceRoot = resourceRoot;
        this.simulationRuntime = simulationRuntime;
    }

    @SuppressWarnings("unchecked")
    public PlaybookRunResult run(Path playbookPath, PlaybookRunConfig config) {
        long start = System.nanoTime();
        SimulationOverlay overlay = null;
        EventCapture eventCapture = new EventCapture();

        try {
            String yaml = Files.readString(playbookPath);
            PlaybookDocument doc = PlaybookParser.parse(yaml);
            Map<String, Object> content = doc.content();

            String playbookName = extractName(doc, content, playbookPath);

            if (config.dryRun() && simulationRuntime != null) {
                overlay = activateSimulation(config);
            }

            var parsed = PlaybookVariableParser.parse(content);

            Map<String, String> callerParams = new LinkedHashMap<>();
            if (config.vars() != null) {
                for (var entry : config.vars().entrySet()) {
                    callerParams.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }

            if (!parsed.params().isEmpty()) {
                io.casehub.yaml.core.module.ParameterValidator.validateOrThrow(
                        parsed.params(), callerParams);
            }

            var steps = (List<Map<String, Object>>) content.get("steps");
            if (steps == null || steps.isEmpty()) {
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                return new PlaybookRunResult(true, playbookName,
                        playbookPath.toString(), durationMs,
                        StepTraceNode.leaf(0, playbookName, "playbook", null,
                                StepOutcome.EXECUTED, durationMs, Map.of()),
                        eventCapture.all(), Map.of(), null);
            }

            var runServices = new io.casehub.yaml.plugin.api.MapServiceRegistry();
            runServices.register(EventSink.class, (EventSink) eventCapture::addEvent);

            List<ResolvedStep> resolved = Walker.resolve(steps, pluginRegistry);
            var runner = new CatalogStepRunner(pluginRegistry, runServices);
            var traceCollector = new StepTraceCollector();
            var evaluator = new StructuralStepEvaluator(
                    new io.casehub.yaml.core.condition.ConditionEvaluator(null))
                    .withListener(traceCollector);

            VariableResolver resolver = buildResolver(parsed, callerParams);

            Map<String, Object> outputVars = new LinkedHashMap<>();
            boolean allPassed = true;
            String errorMsg = null;

            for (int i = 0; i < resolved.size(); i++) {
                var step = resolved.get(i);
                Result result = evaluator.evaluate(step, resolver, runner);

                if (!result.isSuccess()) {
                    allPassed = false;
                    errorMsg = result instanceof Result.Failure f ? f.message() : "Step failed";
                    break;
                }

                if (result.output() != null) {
                    outputVars.putAll(result.output());
                }

                if (step instanceof ResolvedStep.PluginStep ps) {
                    var capture = ps.decorators().get("capture");
                    if (capture != null && result.output() != null) {
                        Map<String, Object> capturedOutput = result.output();
                        resolver = resolver.withObjectScope(
                                String.valueOf(capture), capturedOutput::get);
                    }
                }
            }

            long durationMs = (System.nanoTime() - start) / 1_000_000;
            var rootTrace = new StepTraceNode(0, playbookName, "playbook", null,
                    allPassed ? StepOutcome.EXECUTED : StepOutcome.FAILED,
                    durationMs, Map.of(), traceCollector.rootChildren());

            return new PlaybookRunResult(allPassed, playbookName,
                    playbookPath.toString(), durationMs, rootTrace,
                    eventCapture.all(), outputVars, errorMsg);

        } catch (io.casehub.yaml.core.module.ParameterValidationException e) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            return new PlaybookRunResult(false, playbookPath.getFileName().toString(),
                    playbookPath.toString(), durationMs, null, List.of(), Map.of(),
                    "Parameter validation failed: " + e.getMessage());
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            return new PlaybookRunResult(false, playbookPath.getFileName().toString(),
                    playbookPath.toString(), durationMs, null, List.of(), Map.of(),
                    e.getMessage());
        } finally {
            if (overlay != null && simulationRuntime != null) {
                simulationRuntime.popOverlay(overlay);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public PlaybookValidationResult validate(Path playbookPath) {
        try {
            String yaml = Files.readString(playbookPath);
            PlaybookDocument doc = PlaybookParser.parse(yaml);
            Map<String, Object> content = doc.content();
            PlaybookFrontMatter frontMatter = doc.frontMatter();

            var steps = (List<Map<String, Object>>) content.get("steps");
            if (steps == null || steps.isEmpty()) {
                return new PlaybookValidationResult(true, List.of(), frontMatter);
            }

            Walker.resolve(steps, pluginRegistry);
            return new PlaybookValidationResult(true, List.of(), frontMatter);
        } catch (Exception e) {
            return new PlaybookValidationResult(false, List.of(e.getMessage()), null);
        }
    }

    private SimulationOverlay activateSimulation(PlaybookRunConfig config) {
        if (config.corpusPath() != null) {
            Path corpusFile = resourceRoot.resolve(config.corpusPath());
            var parsed = SimulateBlockParser.parse(loadYamlMap(corpusFile), resourceRoot);
            return simulationRuntime.pushOverlay(parsed.config(), parsed.corpus());
        }
        return simulationRuntime.pushOverlay(MapSimulationConfig.builder().build());
    }

    private static String extractName(PlaybookDocument doc, Map<String, Object> content, Path path) {
        if (doc.hasPlaybookHeader() && doc.frontMatter().name() != null) {
            return doc.frontMatter().name();
        }
        return (String) content.getOrDefault("name", path.getFileName().toString());
    }

    private static VariableResolver buildResolver(
            PlaybookVariableParser.ParsedVariables parsed, Map<String, String> callerParams) {
        VariableResolver resolver = parsed.params().isEmpty() && parsed.vars().values().isEmpty()
                ? new VariableResolver(Map.of(), Set.of("events"))
                : VariableResolver.forParams(parsed.params(), callerParams, Set.of("events"));

        if (!parsed.vars().values().isEmpty()) {
            resolver = resolver.withObjectScope("var",
                    name -> parsed.vars().values().get(name));
        }
        return resolver;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYamlMap(Path path) {
        try {
            var mapper = new ObjectMapper(new YAMLFactory());
            return mapper.readValue(Files.newInputStream(path), Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load: " + path, e);
        }
    }

    static String resolveStepType(ResolvedStep step) {
        return switch (step) {
            case ResolvedStep.PluginStep ignored -> "plugin";
            case ResolvedStep.InvokeStep ignored -> "invoke";
            case ResolvedStep.BlockStep ignored -> "block";
            case ResolvedStep.IfElseStep ignored -> "if-else";
            case ResolvedStep.MatchStep ignored -> "match";
            case ResolvedStep.ParallelStep ignored -> "parallel";
            case ResolvedStep.TryCatchFinallyStep ignored -> "try-catch-finally";
            case ResolvedStep.SelectStep ignored -> "select";
            case ResolvedStep.BarrierStep ignored -> "barrier";
            case ResolvedStep.QuorumStep ignored -> "quorum";
            case ResolvedStep.ControlStep ignored -> "control";
            case ResolvedStep.RaceStep ignored -> "race";
            case ResolvedStep.FusedPipelineStep ignored -> "fused-pipeline";
        };
    }
}
