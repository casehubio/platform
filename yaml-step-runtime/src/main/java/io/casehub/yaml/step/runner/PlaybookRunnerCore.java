package io.casehub.yaml.step.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.core.annotation.AnnotationMap;
import io.casehub.yaml.core.error.LocationIndex;
import io.casehub.yaml.core.error.ParseError;
import io.casehub.yaml.core.error.SourceLocation;
import io.casehub.yaml.core.error.YamlError;
import io.casehub.yaml.core.error.YamlErrorCollector;
import io.casehub.yaml.core.module.ModuleExpander;
import io.casehub.yaml.core.module.TypedExpandedModule;
import io.casehub.yaml.core.module.YamlImport;
import io.casehub.yaml.core.module.YamlModule;
import io.casehub.yaml.core.playbook.PlaybookDocument;
import io.casehub.yaml.core.playbook.PlaybookFrontMatter;
import io.casehub.yaml.core.playbook.PlaybookVariableParser;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.resolver.VariableSource;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlaybookRunnerCore {

    private static final ObjectMapper CONVERT_MAPPER = new ObjectMapper();

    private final PluginRegistry pluginRegistry;
    private final ServiceRegistry serviceRegistry;
    private final Path resourceRoot;
    private final SimulationActivator simulationActivator;
    private final ModuleLoader moduleLoader;

    public PlaybookRunnerCore(PluginRegistry pluginRegistry,
                              ServiceRegistry serviceRegistry,
                              Path resourceRoot,
                              SimulationActivator simulationActivator) {
        this(pluginRegistry, serviceRegistry, resourceRoot, simulationActivator, null);
    }

    public PlaybookRunnerCore(PluginRegistry pluginRegistry,
                              ServiceRegistry serviceRegistry,
                              Path resourceRoot,
                              SimulationActivator simulationActivator,
                              ModuleLoader moduleLoader) {
        this.pluginRegistry = pluginRegistry;
        this.serviceRegistry = serviceRegistry;
        this.resourceRoot = resourceRoot;
        this.simulationActivator = simulationActivator;
        this.moduleLoader = moduleLoader;
    }

    @SuppressWarnings("unchecked")
    public PlaybookRunResult run(Path playbookPath, PlaybookRunConfig config) {
        long          start            = System.nanoTime();
        AutoCloseable simulationHandle = null;
        EventCapture  eventCapture     = new EventCapture();

        try {
            String              yaml    = Files.readString(playbookPath);
            PlaybookDocument    doc     = PlaybookParser.parse(yaml);
            Map<String, Object> content = new LinkedHashMap<>(doc.content());

            ImportExpansionResult            importResult       = expandImports(content, playbookPath);
            VariableSource                   moduleOutputSource = importResult != null ? importResult.outputSource() : null;
            Map<String, Map<String, String>> moduleScopes       = importResult != null ? importResult.moduleScopes() : Map.of();

            String playbookName = extractName(doc, content, playbookPath);

            if (config.dryRun() && simulationActivator != null) {
                simulationHandle = simulationActivator.activate(config, playbookPath);
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

            VariableResolver resolver = buildResolver(parsed, callerParams, moduleOutputSource, moduleScopes);

            if (importResult != null && !importResult.expandedStepSections().isEmpty()) {
                Set<String> inactiveAliases = evaluateDeferredConditions(
                        importResult.importConditions(), resolver);
                Map<String, Object> withInlinedSteps = PlaybookModuleBridge.expandUseSteps(
                        content, importResult.expandedStepSections(), null, inactiveAliases);
                content.clear();
                content.putAll(withInlinedSteps);
            }

            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) content.get("steps");
            if (steps == null || steps.isEmpty()) {
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                return new PlaybookRunResult(true, playbookName,
                                             playbookPath.toString(), durationMs,
                                             StepTraceNode.leaf(0, playbookName, "playbook", null,
                                                                StepOutcome.EXECUTED, durationMs, Map.of()),
                                             eventCapture.all(), Map.of(), null);
            }

            var runServices = new io.casehub.yaml.plugin.api.MapServiceRegistry(serviceRegistry);
            runServices.register(EventSink.class, (EventSink) eventCapture::addEvent);

            List<ResolvedStep>                   resolved     = Walker.resolve(steps, pluginRegistry);
            var                                  stepRunner   = new CatalogStepRunner(pluginRegistry, runServices);
            java.util.function.Predicate<String> simChecker   = null;
            java.util.function.Consumer<String>  stepNotifier = null;
            if (simulationActivator != null) {
                stepNotifier = simulationActivator.stepCompletionNotifier();
                if (config.dryRun()) {
                    simChecker = simulationActivator.simulationChecker();
                }
            }
            var traceCollector   = new StepTraceCollector(simChecker, stepNotifier);
            var captureCollector = new io.casehub.yaml.step.eval.CaptureCollector();

            try (var scope = new io.casehub.yaml.core.orchestration.DefaultExecutionScope()) {
                var conditionEvaluator = new io.casehub.yaml.core.condition.ConditionEvaluator(null);
                Map<String, io.casehub.yaml.step.catalog.ResolvedStep> stepIndex =
                        io.casehub.yaml.step.eval.StepIndexBuilder.build(resolved);
                io.casehub.yaml.step.eval.StepLookup stepLookup = stepIndex::get;

                var evaluator = new StructuralStepEvaluator(conditionEvaluator, scope)
                                        .withListener(traceCollector)
                                        .withCaptureCollector(captureCollector);

                evaluator.preRegisterLatches(resolved);

                Map<String, Object> outputVars = new LinkedHashMap<>();
                boolean             allPassed  = true;
                String              errorMsg   = null;

                for (int i = 0; i < resolved.size(); i++) {
                    var    step   = resolved.get(i);
                    Result result = evaluator.evaluate(step, resolver, stepRunner);

                    if (!result.isSuccess()) {
                        allPassed = false;
                        errorMsg  = result instanceof Result.Failure f ? f.message() : "Step failed";
                        break;
                    }

                    if (result.output() != null) {
                        outputVars.putAll(result.output());
                    }

                    Object captureVal = step.decorators().get("capture");
                    if (captureVal != null) {
                        String              captureName = String.valueOf(captureVal);
                        Map<String, Object> captured    = captureCollector.get(captureName);
                        if (captured != null) {
                            resolver = resolver.withObjectScope(captureName, captured::get);
                        }
                    }
                }

                var primitiveSnapshot = scope.snapshotPrimitives();

                long durationMs = (System.nanoTime() - start) / 1_000_000;
                var rootTrace = new StepTraceNode(0, playbookName, "playbook", null,
                                                  allPassed ? StepOutcome.EXECUTED : StepOutcome.FAILED,
                                                  durationMs, Map.of(), traceCollector.rootChildren());

                return new PlaybookRunResult(allPassed, playbookName,
                                             playbookPath.toString(), durationMs, rootTrace,
                                             eventCapture.all(), outputVars, captureCollector.all(),
                                             primitiveSnapshot, errorMsg);
            }

        } catch (io.casehub.yaml.core.module.ParameterValidationException e) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            return new PlaybookRunResult(false, playbookPath.getFileName().toString(),
                                         playbookPath.toString(), durationMs, null, List.of(), Map.of(),
                                         "Parameter validation failed: " + e.getMessage());
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            return new PlaybookRunResult(false, playbookPath.getFileName().toString(),
                                         playbookPath.toString(), durationMs, null, List.of(), Map.of(),
                                         e.getMessage() != null ? e.getMessage() : e.toString());
        } finally {
            if (simulationHandle != null) {
                try {simulationHandle.close();} catch (Exception ignored) {}
            }
        }
    }

    private static final String INLINE_SOURCE = "<inline>";

    @SuppressWarnings("unchecked")
    public PlaybookRunResult run(String yamlContent, PlaybookRunConfig config) {
        long         start        = System.nanoTime();
        EventCapture eventCapture = new EventCapture();

        try {
            PlaybookDocument    doc     = PlaybookParser.parse(yamlContent);
            Map<String, Object> content = new LinkedHashMap<>(doc.content());

            @SuppressWarnings("unchecked")
            var rawImports = (List<Map<String, Object>>) content.get("imports");
            if (rawImports != null && !rawImports.isEmpty()) {
                throw new IllegalStateException(
                        "Playbook imports are not supported for inline YAML content — " +
                        "import expansion requires a filesystem path for module resolution");
            }

            String playbookName = extractNameInline(doc, content);

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

            VariableResolver resolver = buildResolver(parsed, callerParams, null, Map.of());

            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) content.get("steps");
            if (steps == null || steps.isEmpty()) {
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                return new PlaybookRunResult(true, playbookName,
                                             INLINE_SOURCE, durationMs,
                                             StepTraceNode.leaf(0, playbookName, "playbook", null,
                                                                StepOutcome.EXECUTED, durationMs, Map.of()),
                                             eventCapture.all(), Map.of(), null);
            }

            var runServices = new io.casehub.yaml.plugin.api.MapServiceRegistry(serviceRegistry);
            runServices.register(EventSink.class, (EventSink) eventCapture::addEvent);

            List<ResolvedStep>                   resolved     = Walker.resolve(steps, pluginRegistry);
            var                                  stepRunner   = new CatalogStepRunner(pluginRegistry, runServices);
            java.util.function.Predicate<String> simChecker   = null;
            java.util.function.Consumer<String>  stepNotifier = null;
            if (simulationActivator != null) {
                stepNotifier = simulationActivator.stepCompletionNotifier();
            }
            var traceCollector   = new StepTraceCollector(simChecker, stepNotifier);
            var captureCollector = new io.casehub.yaml.step.eval.CaptureCollector();

            try (var scope = new io.casehub.yaml.core.orchestration.DefaultExecutionScope()) {
                var conditionEvaluator = new io.casehub.yaml.core.condition.ConditionEvaluator(null);
                Map<String, io.casehub.yaml.step.catalog.ResolvedStep> stepIndex =
                        io.casehub.yaml.step.eval.StepIndexBuilder.build(resolved);
                io.casehub.yaml.step.eval.StepLookup stepLookup = stepIndex::get;

                var evaluator = new StructuralStepEvaluator(conditionEvaluator, scope)
                                        .withListener(traceCollector)
                                        .withCaptureCollector(captureCollector);

                evaluator.preRegisterLatches(resolved);

                Map<String, Object> outputVars = new LinkedHashMap<>();
                boolean             allPassed  = true;
                String              errorMsg   = null;

                for (int i = 0; i < resolved.size(); i++) {
                    var    step   = resolved.get(i);
                    Result result = evaluator.evaluate(step, resolver, stepRunner);

                    if (!result.isSuccess()) {
                        allPassed = false;
                        errorMsg  = result instanceof Result.Failure f ? f.message() : "Step failed";
                        break;
                    }

                    if (result.output() != null) {
                        outputVars.putAll(result.output());
                    }

                    Object captureVal = step.decorators().get("capture");
                    if (captureVal != null) {
                        String              captureName = String.valueOf(captureVal);
                        Map<String, Object> captured    = captureCollector.get(captureName);
                        if (captured != null) {
                            resolver = resolver.withObjectScope(captureName, captured::get);
                        }
                    }
                }

                var primitiveSnapshot = scope.snapshotPrimitives();

                long durationMs = (System.nanoTime() - start) / 1_000_000;
                var rootTrace = new StepTraceNode(0, playbookName, "playbook", null,
                                                  allPassed ? StepOutcome.EXECUTED : StepOutcome.FAILED,
                                                  durationMs, Map.of(), traceCollector.rootChildren());

                return new PlaybookRunResult(allPassed, playbookName,
                                             INLINE_SOURCE, durationMs, rootTrace,
                                             eventCapture.all(), outputVars, captureCollector.all(),
                                             primitiveSnapshot, errorMsg);
            }

        } catch (io.casehub.yaml.core.module.ParameterValidationException e) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            return new PlaybookRunResult(false, INLINE_SOURCE,
                                         INLINE_SOURCE, durationMs, null, List.of(), Map.of(),
                                         "Parameter validation failed: " + e.getMessage());
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            return new PlaybookRunResult(false, INLINE_SOURCE,
                                         INLINE_SOURCE, durationMs, null, List.of(), Map.of(),
                                         e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }


    @SuppressWarnings("unchecked")
    public PlaybookValidationResult validate(Path playbookPath) {
        try {
            String                yaml         = Files.readString(playbookPath);
            PlaybookDocument      doc          = PlaybookParser.parse(yaml);
            Map<String, Object>   content      = new LinkedHashMap<>(doc.content());
            ImportExpansionResult importResult = expandImports(content, playbookPath);
            if (importResult != null && !importResult.expandedStepSections().isEmpty()) {
                Map<String, Object> withInlinedSteps = PlaybookModuleBridge.expandUseSteps(
                        content, importResult.expandedStepSections());
                content.clear();
                content.putAll(withInlinedSteps);
            }
            PlaybookFrontMatter frontMatter = doc.frontMatter();

            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) content.get("steps");
            if (steps == null || steps.isEmpty()) {
                return new PlaybookValidationResult(true, List.of(), frontMatter);
            }

            Walker.resolve(steps, pluginRegistry);
            return new PlaybookValidationResult(true, List.of(), frontMatter);
        } catch (Exception e) {
            var diag = new ParseError.StructureError(
                    null, e.getMessage(), SourceLocation.UNKNOWN, e);
            return new PlaybookValidationResult(false, List.of(diag), null);
        }
    }

    @SuppressWarnings("unchecked")
    public PlaybookValidationResult validate(Path playbookPath, ValidationContext ctx) {
        try {
            String                yaml         = Files.readString(playbookPath);
            String                fileName     = playbookPath.toString();
            var                   parsed       = PlaybookParser.parseWithLocations(yaml, fileName);
            PlaybookDocument      doc          = parsed.document();
            var                   locations    = parsed.locations();
            Map<String, Object>   content      = new LinkedHashMap<>(doc.content());
            ImportExpansionResult importResult = expandImports(content, playbookPath, locations);
            if (importResult != null && !importResult.expandedStepSections().isEmpty()) {
                Map<String, Object> withInlinedSteps = PlaybookModuleBridge.expandUseSteps(
                        content, importResult.expandedStepSections(), locations);
                content.clear();
                content.putAll(withInlinedSteps);
            }
            PlaybookFrontMatter frontMatter = doc.frontMatter();

            var collector = new YamlErrorCollector();

            if (content.containsKey("steps") && "assisted".equals(ctx.schema())) {
                collector.add(new ParseError.StructureError(
                        null, "Remove the 'steps:' wrapper — use a bare YAML list instead",
                        locations.locationOf(content, fileName), null));
            }

            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) content.get("steps");
            if (steps == null || steps.isEmpty()) {
                List<YamlError> diags = collector.errors();
                return new PlaybookValidationResult(diags.isEmpty(), diags, frontMatter);
            }

            var annotations = AnnotationMap.empty();
            Walker.resolve(steps, pluginRegistry, annotations, collector, fileName, locations);

            List<YamlError>            diagnostics = collector.errors();
            List<ValidationSuggestion> suggestions = ValidationSuggestionGenerator.generate(diagnostics, pluginRegistry);

            return new PlaybookValidationResult(
                    !collector.hasErrors(), diagnostics, frontMatter, suggestions, null);
        } catch (Exception e) {
            var diag = new ParseError.StructureError(
                    null, e.getMessage(), SourceLocation.UNKNOWN, e);
            return new PlaybookValidationResult(false, List.of(diag), null);
        }
    }


    public static Path discoverSimulationYaml(Path playbookPath) {
        String fileName = playbookPath.getFileName().toString();
        String base = fileName.endsWith(".playbook.yaml") ? fileName.substring(0, fileName.length() - ".playbook.yaml".length())
                                                          : fileName.endsWith(".playbook.yml") ? fileName.substring(0, fileName.length() - ".playbook.yml".length())
                                                                                               : fileName.endsWith(".yaml") ? fileName.substring(0, fileName.length() - ".yaml".length())
                                                                                                                            : fileName.endsWith(".yml") ? fileName.substring(0, fileName.length() - ".yml".length())
                                                                                                                                                        : fileName;

        Path dir = playbookPath.getParent();
        while (dir != null) {
            Path candidate = dir.resolve(base + ".simulation.yaml");
            if (Files.exists(candidate)) {return candidate;}
            candidate = dir.resolve(base + ".simulation.yml");
            if (Files.exists(candidate)) {return candidate;}
            candidate = dir.resolve("simulation.yaml");
            if (Files.exists(candidate)) {return candidate;}
            candidate = dir.resolve("simulation.yml");
            if (Files.exists(candidate)) {return candidate;}
            dir = dir.getParent();
        }
        return null;
    }

    public static Path resolveClasspathRoot(String classpathDir) {
        java.net.URL url = Thread.currentThread().getContextClassLoader().getResource(classpathDir);
        if (url != null && "file".equals(url.getProtocol())) {
            try {
                return Path.of(url.toURI());
            } catch (java.net.URISyntaxException e) {
                return null;
            }
        }
        return null;
    }


    private static String extractName(PlaybookDocument doc, Map<String, Object> content, Path path) {
        if (doc.hasPlaybookHeader() && doc.frontMatter().name() != null) {
            return doc.frontMatter().name();
        }
        return (String) content.getOrDefault("name", path.getFileName().toString());
    }

    private static String extractNameInline(PlaybookDocument doc, Map<String, Object> content) {
        if (doc.hasPlaybookHeader() && doc.frontMatter().name() != null) {
            return doc.frontMatter().name();
        }
        return (String) content.getOrDefault("name", INLINE_SOURCE);
    }


    private record ImportExpansionResult(
            io.casehub.yaml.core.resolver.VariableSource outputSource,
            Map<String, Map<String, String>> moduleScopes,
            Map<String, String> importConditions,
            Map<String, Object> expandedStepSections) {}

    @SuppressWarnings("unchecked")
    private ImportExpansionResult expandImports(Map<String, Object> content, Path playbookPath) {
        return expandImports(content, playbookPath, null);
    }

    @SuppressWarnings("unchecked")
    private ImportExpansionResult expandImports(Map<String, Object> content, Path playbookPath,
                                                LocationIndex locations) {
        var rawImports = (List<Map<String, Object>>) content.remove("imports");
        if (rawImports == null || rawImports.isEmpty()) {
            return null;
        }
        if (moduleLoader == null) {
            throw new IllegalStateException(
                    "Playbook uses imports but no ModuleLoader is configured");
        }

        List<YamlImport> imports = rawImports.stream()
                                             .map(raw -> CONVERT_MAPPER.convertValue(raw, YamlImport.class))
                                             .toList();

        Map<String, YamlModule> modules = locations != null
                                          ? moduleLoader.loadModules(imports, playbookPath, locations)
                                          : moduleLoader.loadModules(imports, playbookPath);

        var bridge = new PlaybookModuleBridge();
        TypedExpandedModule<Map<String, Object>> expanded =
                ModuleExpander.expand(imports, modules, content, bridge);

        Map<String, Object> expandedContent = new LinkedHashMap<>(expanded.content());

        @SuppressWarnings("unchecked")
        Map<String, Object> expandedStepSections =
                (Map<String, Object>) expandedContent.remove(PlaybookModuleBridge.EXPANDED_STEP_SECTIONS_KEY);

        content.clear();
        content.putAll(expandedContent);

        return new ImportExpansionResult(expanded.outputSource(), expanded.moduleScopes(),
                                         expanded.importConditions(),
                                         expandedStepSections != null ? expandedStepSections : Map.of());
    }

    private static VariableResolver buildResolver(
            PlaybookVariableParser.ParsedVariables parsed, Map<String, String> callerParams,
            VariableSource moduleOutputSource, Map<String, Map<String, String>> moduleScopes) {
        VariableResolver resolver = parsed.params().isEmpty() && parsed.vars().values().isEmpty()
                                    ? new VariableResolver(Map.of(), Set.of("events"))
                                    : VariableResolver.forParams(parsed.params(), callerParams, Set.of("events"));

        Map<String, Object> mergedVars = new LinkedHashMap<>();
        for (Map<String, String> scope : moduleScopes.values()) {
            mergedVars.putAll(scope);
        }
        mergedVars.putAll(parsed.vars().values());
        if (!mergedVars.isEmpty()) {
            resolver = resolver.withObjectScope("var", mergedVars::get);
        }

        if (moduleOutputSource != null) {
            resolver = resolver.withScope("module", moduleOutputSource);
        }
        return resolver;
    }

    private static Set<String> evaluateDeferredConditions(
            Map<String, String> importConditions, VariableResolver resolver) {
        if (importConditions.isEmpty()) {
            return Set.of();
        }
        Set<String> inactive = new java.util.LinkedHashSet<>();
        for (var entry : importConditions.entrySet()) {
            String alias     = entry.getKey();
            String condition = entry.getValue();
            try {
                String resolved = resolver.resolveString(condition, alias + ".condition");
                if (!resolved.contains("${") && !ModuleExpander.isConditionMet(resolved)) {
                    inactive.add(alias);
                }
            } catch (io.casehub.yaml.core.resolver.UnresolvedVariableException ignored) {
                // still unresolvable at runtime — keep the module active
            }
        }
        return inactive;
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
            case ResolvedStep.ErrorStep ignored -> "error";
        };
    }
}
