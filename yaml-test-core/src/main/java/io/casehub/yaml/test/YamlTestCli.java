package io.casehub.yaml.test;

import io.casehub.platform.simulation.MapSimulationConfig;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import io.casehub.yaml.step.plugin.AssertThatPlugin;
import io.casehub.yaml.step.plugin.EmitEventPlugin;
import io.casehub.yaml.step.plugin.RunPlaybookPlugin;
import io.casehub.yaml.step.plugin.VerifySimulationPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class YamlTestCli {

    private YamlTestCli() {}

    public record ParsedArgs(
            boolean dryRun,
            String corpusPath,
            String profile,
            Map<String, Object> vars,
            String filter,
            String reportFormat,
            String outputDir,
            List<String> paths) {

        public ParsedArgs {
            vars = vars != null ? Map.copyOf(vars) : Map.of();
            paths = paths != null ? List.copyOf(paths) : List.of();
        }
    }

    public static ParsedArgs parseArgs(String[] args) {
        boolean dryRun = false;
        String corpusPath = null;
        String profile = null;
        Map<String, Object> vars = new LinkedHashMap<>();
        String filter = null;
        String reportFormat = null;
        String outputDir = null;
        List<String> paths = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dry-run" -> dryRun = true;
                case "--corpus" -> corpusPath = args[++i];
                case "--profile" -> profile = args[++i];
                case "--var" -> {
                    String kv = args[++i];
                    int eq = kv.indexOf('=');
                    if (eq > 0) {
                        vars.put(kv.substring(0, eq), kv.substring(eq + 1));
                    }
                }
                case "--filter" -> filter = args[++i];
                case "--report" -> reportFormat = args[++i];
                case "--output" -> outputDir = args[++i];
                default -> paths.add(args[i]);
            }
        }

        return new ParsedArgs(dryRun, corpusPath, profile, vars,
                filter, reportFormat, outputDir, paths);
    }

    public static boolean isPlaybookFile(String filename) {
        return (filename.endsWith(".yaml") || filename.endsWith(".yml"))
                && !filename.endsWith(".test.yaml");
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: casehub-test [--dry-run] [--corpus <path>] [--profile <name>] "
                    + "[--var key=value]... [--filter <pattern>] [--report junit-xml] [--output <dir>] <path>...");
            System.exit(1);
        }

        ParsedArgs parsed = parseArgs(args);

        if (parsed.paths().isEmpty()) {
            System.err.println("No paths specified");
            System.exit(1);
        }

        var pluginRegistry = new CompositePluginRegistry();
        pluginRegistry.register(AssertThatPlugin.definition());
        pluginRegistry.register(EmitEventPlugin.definition());
        pluginRegistry.register(RunPlaybookPlugin.definition());
        pluginRegistry.register(VerifySimulationPlugin.definition());

        var serviceRegistry = new MapServiceRegistry();
        var simulationRuntime = new SimulationRuntime(
                MapSimulationConfig.builder().build(), new InMemorySimulationCorpus<>());

        List<TestResult> allResults = new ArrayList<>();
        boolean anyFailed = false;

        for (String pathStr : parsed.paths()) {
            Path root = Path.of(pathStr);

            if (Files.isRegularFile(root) && isPlaybookFile(root.getFileName().toString())) {
                anyFailed |= runPlaybook(root, parsed, pluginRegistry,
                        serviceRegistry, simulationRuntime);
            } else if (Files.isDirectory(root)) {
                anyFailed |= runTestSuite(root, parsed, pluginRegistry,
                        serviceRegistry, simulationRuntime, allResults);
            } else if (Files.isRegularFile(root)) {
                anyFailed |= runTestSuite(root.getParent(), parsed, pluginRegistry,
                        serviceRegistry, simulationRuntime, allResults);
            }
        }

        int total = allResults.size();
        if (total > 0) {
            int passCount = (int) allResults.stream().filter(r -> r.status() == TestStatus.PASS).count();
            int failCount = (int) allResults.stream().filter(r -> r.status() == TestStatus.FAIL).count();
            int errorCount = (int) allResults.stream().filter(r -> r.status() == TestStatus.ERROR).count();
            int skippedCount = (int) allResults.stream().filter(r -> r.status() == TestStatus.SKIP).count();

            System.out.printf("%n%d tests: %d passed, %d failed, %d errors, %d skipped%n",
                    total, passCount, failCount, errorCount, skippedCount);

            if ("junit-xml".equals(parsed.reportFormat()) && parsed.outputDir() != null) {
                writeJunitReport(allResults, parsed.outputDir());
            }
        }

        System.exit(anyFailed ? 1 : 0);
    }

    private static boolean runPlaybook(Path playbookPath, ParsedArgs parsed,
                                        CompositePluginRegistry pluginRegistry,
                                        MapServiceRegistry serviceRegistry,
                                        SimulationRuntime simulationRuntime) {
        var config = new PlaybookRunConfig(parsed.dryRun(), parsed.vars(),
                parsed.corpusPath(), parsed.profile());
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                playbookPath.getParent(), simulationRuntime);

        PlaybookRunResult result = runner.run(playbookPath, config);
        printPlaybookResult(result);
        return !result.success();
    }

    private static void printPlaybookResult(PlaybookRunResult result) {
        String mode = result.trace() != null
                && result.trace().children().stream().anyMatch(c -> c.outcome() == StepOutcome.SIMULATED)
                ? "Dry run" : "Run";
        System.out.printf("%s: %s%n", mode, result.playbookName());

        if (result.trace() != null) {
            for (var entry : result.trace().flatten()) {
                if ("playbook".equals(entry.node().type())) continue;
                String indent = "  ".repeat(entry.depth());
                String action = entry.node().action() != null ? entry.node().action() : entry.node().type();
                String outcomeLabel = entry.node().outcome().name();
                System.out.printf("%s%d  %-10s %s (%dms)%n",
                        indent, entry.node().index() + 1, outcomeLabel,
                        action, entry.node().durationMs());
            }

            long totalSteps = result.trace().children().size();
            long passed = result.trace().children().stream()
                    .filter(c -> c.outcome() == StepOutcome.EXECUTED || c.outcome() == StepOutcome.SIMULATED)
                    .count();
            System.out.printf("%n%d/%d steps succeeded (%dms)%n", passed, totalSteps, result.durationMs());
        }

        if (!result.success() && result.error() != null) {
            System.out.printf("Error: %s%n", result.error());
        }
    }

    private static boolean runTestSuite(Path root, ParsedArgs parsed,
                                         CompositePluginRegistry pluginRegistry,
                                         MapServiceRegistry serviceRegistry,
                                         SimulationRuntime simulationRuntime,
                                         List<TestResult> allResults) {
        var executor = new YamlTestExecutor(pluginRegistry, serviceRegistry,
                root, simulationRuntime);
        var descriptors = YamlTestDiscovery.discover(root);
        boolean anyFailed = false;

        for (var descriptor : descriptors) {
            if (parsed.filter() != null && !descriptor.name().contains(parsed.filter())
                    && !descriptor.file().toString().contains(parsed.filter())) {
                continue;
            }

            List<TestResult> results = executor.execute(descriptor);
            allResults.addAll(results);

            for (var result : results) {
                String icon = switch (result.status()) {
                    case PASS -> "PASS";
                    case FAIL -> "FAIL";
                    case ERROR -> "ERROR";
                    case SKIP -> "SKIP";
                };
                System.out.printf("  %s  %s (%dms)%n", icon, result.name(), result.durationMs());
                if (result.status() == TestStatus.FAIL || result.status() == TestStatus.ERROR) {
                    anyFailed = true;
                    for (var failure : result.failures()) {
                        System.out.printf("       %s%n", failure.message().replace("\n", "\n       "));
                    }
                }
            }
        }

        return anyFailed;
    }

    private static void writeJunitReport(List<TestResult> results, String outputDir) {
        try {
            Path outPath = Path.of(outputDir);
            Files.createDirectories(outPath);
            String xml = TestReport.toJunitXml(results, "casehub-yaml-tests");
            Files.writeString(outPath.resolve("TEST-yaml-tests.xml"), xml);
            System.out.println("Report written to " + outPath.resolve("TEST-yaml-tests.xml"));
        } catch (Exception e) {
            System.err.println("Failed to write report: " + e.getMessage());
        }
    }
}
