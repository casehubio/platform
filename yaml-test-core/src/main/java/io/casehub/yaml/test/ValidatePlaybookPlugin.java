package io.casehub.yaml.test;

import io.casehub.platform.agent.config.PoolDeclaration;
import io.casehub.platform.compiler.CompilationOutcome;
import io.casehub.platform.compiler.CompilationResult;
import io.casehub.platform.compiler.PlaybookCompiler;
import io.casehub.yaml.core.playbook.PlaybookDocument;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Portability;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.plugin.api.ServiceRegistry;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import io.casehub.yaml.step.plugin.TestExecutionContext;
import io.casehub.yaml.step.statemachine.PlaybookParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ValidatePlaybookPlugin implements Action {

    private static final List<String> CATALOG_STUB_ACTIONS = List.of(
            "accumulate", "accumulate-get", "agent-delegate", "agent-invoke",
            "agent-session-open", "agent-session-close", "agent-session-clear",
            "agent-query", "await",
            "chan-consume", "chan-produce", "concurrent",
            "corr-await", "corr-expect", "corr-send",
            "counter-add", "counter-get",
            "deadline-check", "deadline-create", "deadline-work",
            "fail", "flag-check", "flag-set", "flag-toggle",
            "gauge-cas", "gauge-get", "gauge-set",
            "graphql", "graphql-exec",
            "latch-countdown", "latch-create", "latch-status",
            "log", "map-reader", "map-writer", "mcp-call",
            "pipeline-consume", "process-exec",
            "rest-request", "script-run", "sem-worker", "simulated",
            "sm-create", "sm-state", "sm-transition",
            "spawn-task", "stop", "task-join", "task-poll",
            "click", "fill", "navigate", "select", "expand", "collapse",
            "assert", "wait", "show-markdown", "spotlight", "scroll-to-row",
            "editor-insert", "editor-set-content", "editor-replace",
            "editor-delete", "editor-cursor", "editor-highlight", "editor-completion"
    );

    public static Definition definition() {
        return Definition.of("validate-playbook")
                .description("Compiles a playbook and returns structural metadata")
                .execute(new ValidatePlaybookPlugin())
                .build();
    }

    @Override
    public Result execute(Map<String, Object> params, ServiceRegistry services) {
        TestExecutionContext ctx;
        try {
            ctx = services.lookup(TestExecutionContext.class);
        } catch (IllegalArgumentException e) {
            return Result.failed("validate-playbook requires TestExecutionContext");
        }

        if (params.containsKey("directory")) {
            return executeDirectory(params, ctx);
        }

        String file = params.containsKey("_subject")
                ? String.valueOf(params.get("_subject"))
                : (String) params.get("file");

        if (file == null || file.isBlank()) {
            return Result.failed("validate-playbook requires a file path");
        }

        Path playbookPath = ctx.resourceRoot().resolve(file);
        if (!Files.exists(playbookPath)) {
            return Result.failed("Playbook not found: " + playbookPath);
        }

        return compileSingleFile(playbookPath);
    }

    private Result compileSingleFile(Path playbookPath) {
        try {
            String yaml = Files.readString(playbookPath);
            PlaybookDocument doc = PlaybookParser.parse(yaml);
            CompilationOutcome outcome = new PlaybookCompiler().compile(doc);

            if (outcome instanceof CompilationOutcome.Failure failure) {
                return Result.of(Map.of(
                        "valid", false,
                        "errors", failure.diagnostics().stream()
                                .map(Object::toString).toList(),
                        "stepCount", 0,
                        "poolDeclarations", List.of(),
                        "pools", Map.of(),
                        "stepNames", List.of(),
                        "hasFork", false,
                        "hasClearStep", false));
            }

            CompilationResult result = ((CompilationOutcome.Success) outcome).result();
            var tasks = result.workflow().getDo();

            List<String> stepNames = new ArrayList<>();
            boolean hasFork = false;
            boolean hasClear = false;
            for (var task : tasks) {
                stepNames.add(task.getName());
                if (task.getTask().getForkTask() != null) {
                    hasFork = true;
                }
                if (task.getName().startsWith("agent-session-clear")) {
                    hasClear = true;
                }
            }

            List<Map<String, Object>> poolDeclsList = new ArrayList<>();
            Map<String, Map<String, Object>> poolsByName = new LinkedHashMap<>();
            for (PoolDeclaration pd : result.poolDeclarations()) {
                Map<String, Object> poolMap = new LinkedHashMap<>();
                poolMap.put("name", pd.name());
                poolMap.put("maxActive", pd.maxActive());
                poolMap.put("clearing", pd.clearing() != null ? pd.clearing() : Map.of());
                poolMap.put("salience", pd.salience());
                poolDeclsList.add(poolMap);
                poolsByName.put(pd.name(), poolMap);
            }

            return Result.of(Map.of(
                    "valid", true,
                    "errors", List.of(),
                    "stepCount", tasks.size(),
                    "poolDeclarations", poolDeclsList,
                    "pools", (Object) poolsByName,
                    "stepNames", stepNames,
                    "hasFork", hasFork,
                    "hasClearStep", hasClear));

        } catch (Exception e) {
            return Result.of(Map.of(
                    "valid", false,
                    "errors", List.of(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()),
                    "stepCount", 0,
                    "poolDeclarations", List.of(),
                    "pools", Map.of(),
                    "stepNames", List.of(),
                    "hasFork", false,
                    "hasClearStep", false));
        }
    }

    private Result executeDirectory(Map<String, Object> params, TestExecutionContext ctx) {
        String dir = String.valueOf(params.get("directory"));
        Path playbooksDir = ctx.resourceRoot().resolve(dir);

        if (!java.nio.file.Files.isDirectory(playbooksDir)) {
            return Result.failed("Directory not found: " + playbooksDir);
        }

        try {
            List<Path> yamlFiles;
            try (var stream = java.nio.file.Files.walk(playbooksDir)) {
                yamlFiles = stream
                        .filter(java.nio.file.Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                        .filter(p -> !p.getFileName().toString().startsWith("_"))
                        .sorted()
                        .toList();
            }

            int validatedCount = 0;
            List<Map<String, Object>> errors = new ArrayList<>();
            var registry = buildCatalogRegistry(ctx);

            for (Path yamlPath : yamlFiles) {
                String relativePath = playbooksDir.relativize(yamlPath).toString();
                var runnerCore = new PlaybookRunnerCore(
                        registry, ctx.serviceRegistry(),
                        playbooksDir, null, new FileSystemModuleLoader());
                PlaybookValidationResult valResult = runnerCore.validate(yamlPath);

                if (!valResult.valid()) {
                    errors.add(Map.of("file", relativePath,
                            "error", String.join("; ", valResult.errors())));
                }
                validatedCount++;
            }

            return Result.of(Map.of(
                    "valid", errors.isEmpty(),
                    "validatedCount", validatedCount,
                    "errors", (Object) errors));

        } catch (Exception e) {
            return Result.failed("Directory scan failed: " + e.getMessage());
        }
    }

    private static CompositePluginRegistry buildCatalogRegistry(TestExecutionContext ctx) {
        var registry = new CompositePluginRegistry();
        for (String action : CATALOG_STUB_ACTIONS) {
            registry.register(new Definition(action, null, Map.of(), Map.of(),
                                             Portability.BOTH, (p, s) -> Result.of(Map.of()), null));
        }
        return registry;
    }
}
