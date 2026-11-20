package io.casehub.yaml.test;

import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.plugin.api.ServiceRegistry;
import io.casehub.yaml.step.plugin.TestExecutionContext;
import io.casehub.yaml.step.runner.FileSystemModuleLoader;
import io.casehub.yaml.step.runner.PlaybookRunConfig;
import io.casehub.yaml.step.runner.PlaybookRunResult;
import io.casehub.yaml.step.runner.PlaybookRunnerCore;
import io.casehub.yaml.step.runner.StepOutcome;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

public final class RunPlaybookPlugin implements Action {

    public static Definition definition() {
        return Definition.of("run-playbook")
                .description("Executes another playbook and captures results")
                .execute(new RunPlaybookPlugin())
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result execute(Map<String, Object> params, ServiceRegistry services) {
        TestExecutionContext ctx;
        try {
            ctx = services.lookup(TestExecutionContext.class);
        } catch (IllegalArgumentException e) {
            return Result.failed("run-playbook requires TestExecutionContext in ServiceRegistry");
        }

        String file = params.containsKey("_subject")
                      ? String.valueOf(params.get("_subject"))
                      : (String) params.get("file");

        if (file == null || file.isBlank()) {
            return Result.failed("run-playbook requires a file path or qualified ID");
        }

        Path playbookPath;
        try {
            playbookPath = resolvePlaybook(file, ctx);
        } catch (IllegalArgumentException e) {
            return Result.failed(e.getMessage());
        }

        Map<String, Object> callerVars = params.containsKey("vars")
                                         ? (Map<String, Object>) params.get("vars")
                                         : Map.of();

        SimulationRuntime simRuntime = ctx.simulationRuntime() instanceof SimulationRuntime sr ? sr : null;
        boolean           dryRun     = simRuntime != null;

        var config    = new PlaybookRunConfig(dryRun, callerVars, null, null);
        var activator = simRuntime != null ? new SimulationRuntimeActivator(simRuntime, ctx.resourceRoot()) : null;
        var runner = new PlaybookRunnerCore(ctx.pluginRegistry(), ctx.serviceRegistry(),
                                            ctx.resourceRoot(), activator, new FileSystemModuleLoader());

        PlaybookRunResult runResult = runner.run(playbookPath, config);

        var stepEvents = new ArrayList<Map<String, Object>>();
        if (runResult.trace() != null) {
            for (var entry : runResult.trace().flatten()) {
                var node = entry.node();
                if (!"playbook".equals(node.type())) {
                    stepEvents.add(Map.of(
                            "step", node.index() + 1,
                            "action", node.action() != null ? node.action() : node.type(),
                            "success", node.outcome() == StepOutcome.EXECUTED
                                       || node.outcome() == StepOutcome.SIMULATED));
                }
            }
        }

        var resultMap = new java.util.LinkedHashMap<String, Object>();
        resultMap.put("succeeded", runResult.success());
        resultMap.put("output", runResult.output());
        resultMap.put("captures", runResult.captures());
        resultMap.put("stepCount", stepEvents.size());
        resultMap.put("duration", runResult.durationMs());
        resultMap.put("stepEvents", stepEvents);
        resultMap.put("error", runResult.error() != null ? runResult.error() : "");
        if (runResult.primitiveStates() != null) {
            for (var entry : runResult.primitiveStates().entrySet()) {
                resultMap.put(entry.getKey(), entry.getValue());
            }
        }
        if (simRuntime != null) {
            var smStates  = simRuntime.stateMachineStates();
            var sanitized = new java.util.LinkedHashMap<String, Object>();
            for (var smEntry : smStates.entrySet()) {
                sanitized.put(smEntry.getKey().replace('.', '_'), smEntry.getValue());
            }
            resultMap.put("stateMachines", sanitized);
        }
        return Result.of(resultMap);
    }

    private static final String PLAYBOOKS_ROOT  = "playbooks";
    private static final String PLAYBOOK_SUFFIX = ".playbook.yaml";

    static Path resolvePlaybook(String ref, TestExecutionContext ctx) {
        Path filePath = ctx.resourceRoot().resolve(ref);
        if (Files.exists(filePath)) {
            return filePath;
        }
        if (ref.endsWith(".yaml") || ref.startsWith(".") || ref.startsWith("/")) {
            throw new IllegalArgumentException("Playbook not found: " + filePath);
        }
        String qualifiedRef = ref.contains("/") ? ref : derivePackage(ctx) + ref;
        String bare     = qualifiedRef + PLAYBOOK_SUFFIX;
        String prefixed = PLAYBOOKS_ROOT + "/" + bare;
        var    classLoader = Thread.currentThread().getContextClassLoader();
        try {
            Path found = scanClasspath(classLoader, bare);
            if (found == null) {
                found = scanClasspath(classLoader, prefixed);
            }
            if (found != null) {
                return found;
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to scan classpath for playbooks", e);
        }
        throw new IllegalArgumentException("Playbook not found: '" + ref + "' (resolved as '" + qualifiedRef + "')");
    }

    private static Path scanClasspath(ClassLoader cl, String resourcePath) throws java.io.IOException {
        var urls = cl.getResources(resourcePath);
        while (urls.hasMoreElements()) {
            var url = urls.nextElement();
            try {
                Path resolved = toPath(url);
                if (Files.exists(resolved)) {
                    return resolved;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static Path toPath(java.net.URL url) throws Exception {
        var uri = url.toURI();
        if ("file".equals(uri.getScheme())) {
            return Path.of(uri);
        }
        if ("jar".equals(uri.getScheme())) {
            String[] parts = uri.toString().split("!", 2);
            var jarUri = java.net.URI.create(parts[0]);
            var env = java.util.Map.<String, String>of();
            java.nio.file.FileSystem fs;
            try {
                fs = java.nio.file.FileSystems.getFileSystem(jarUri);
            } catch (java.nio.file.FileSystemNotFoundException e) {
                fs = java.nio.file.FileSystems.newFileSystem(jarUri, env);
            }
            return fs.getPath(parts[1]);
        }
        return Path.of(uri);
    }

    private static String derivePackage(TestExecutionContext ctx) {
        Path resourceRoot = ctx.resourceRoot();
        for (Path ancestor = resourceRoot; ancestor != null; ancestor = ancestor.getParent()) {
            if (PLAYBOOKS_ROOT.equals(ancestor.getFileName().toString())) {
                String relative = ancestor.relativize(resourceRoot).toString().replace('\\', '/');
                return relative.isEmpty() ? "" : relative + "/";
            }
        }
        return "";
    }
}
