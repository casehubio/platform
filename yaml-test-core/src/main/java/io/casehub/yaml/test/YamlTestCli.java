package io.casehub.yaml.test;

import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import io.casehub.yaml.step.plugin.AssertThatPlugin;
import io.casehub.yaml.step.plugin.EmitEventPlugin;
import io.casehub.yaml.step.plugin.RunPlaybookPlugin;
import io.casehub.yaml.step.plugin.VerifySimulationPlugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class YamlTestCli {

    private YamlTestCli() {}

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: casehub-test [--filter <pattern>] [--report junit-xml] [--output <dir>] <path>...");
            System.exit(1);
        }

        String filter = null;
        String reportFormat = null;
        String outputDir = null;
        List<String> paths = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--filter" -> filter = args[++i];
                case "--report" -> reportFormat = args[++i];
                case "--output" -> outputDir = args[++i];
                default -> paths.add(args[i]);
            }
        }

        if (paths.isEmpty()) {
            System.err.println("No test paths specified");
            System.exit(1);
        }

        var pluginRegistry = new CompositePluginRegistry();
        pluginRegistry.register(AssertThatPlugin.definition());
        pluginRegistry.register(EmitEventPlugin.definition());
        pluginRegistry.register(RunPlaybookPlugin.definition());
        pluginRegistry.register(VerifySimulationPlugin.definition());

        var serviceRegistry = new MapServiceRegistry();
        List<TestResult> allResults = new ArrayList<>();
        boolean anyFailed = false;

        for (String pathStr : paths) {
            Path root = Path.of(pathStr);
            var executor = new YamlTestExecutor(pluginRegistry, serviceRegistry, root);
            var descriptors = YamlTestDiscovery.discover(root);

            for (var descriptor : descriptors) {
                if (filter != null && !descriptor.name().contains(filter)
                        && !descriptor.file().toString().contains(filter)) {
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
        }

        int total = allResults.size();
        int passed = (int) allResults.stream().filter(r -> r.status() == TestStatus.PASS).count();
        int failed = (int) allResults.stream().filter(r -> r.status() == TestStatus.FAIL).count();
        int errors = (int) allResults.stream().filter(r -> r.status() == TestStatus.ERROR).count();
        int skipped = (int) allResults.stream().filter(r -> r.status() == TestStatus.SKIP).count();

        System.out.printf("%n%d tests: %d passed, %d failed, %d errors, %d skipped%n",
                total, passed, failed, errors, skipped);

        if ("junit-xml".equals(reportFormat) && outputDir != null) {
            try {
                Path outPath = Path.of(outputDir);
                java.nio.file.Files.createDirectories(outPath);
                String xml = TestReport.toJunitXml(allResults, "casehub-yaml-tests");
                java.nio.file.Files.writeString(outPath.resolve("TEST-yaml-tests.xml"), xml);
                System.out.println("Report written to " + outPath.resolve("TEST-yaml-tests.xml"));
            } catch (Exception e) {
                System.err.println("Failed to write report: " + e.getMessage());
            }
        }

        System.exit(anyFailed ? 1 : 0);
    }
}
