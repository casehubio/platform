package io.casehub.template.registry.llm;

import io.casehub.template.registry.CorpusQualityHarness;
import io.casehub.template.registry.CorpusQualityReport;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.TemplateReport;
import io.casehub.template.registry.UnifiedTemplateRegistry;
import io.casehub.template.registry.VariationFixture;
import io.casehub.template.registry.VariationFixtureWriter;
import io.casehub.template.registry.VariationSeeder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CorpusVariationGenerator {

    public static CorpusQualityReport run(TemplateNamespace namespace,
                                           VariationSeeder seeder,
                                           UnifiedTemplateRegistry registry,
                                           Path outputPath,
                                           double threshold) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(seeder, "seeder");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(outputPath, "outputPath");

        List<VariationFixture> allFixtures = new ArrayList<>();

        for (var entry : namespace.templates().entrySet()) {
            TemplateDefinition def = entry.getValue();
            List<String> variations = seeder.seed(def, namespace.language());

            for (String variation : variations) {
                TemplateMatch match = registry.match(namespace.name(), variation);
                Map<String, String> fields = Map.of();
                if (match instanceof TemplateMatch.Matched m) {
                    fields = m.fields();
                }
                allFixtures.add(new VariationFixture(
                    def.name(), variation, true, fields));
            }
        }

        var harness = new CorpusQualityHarness(registry);
        var report = harness.evaluate(namespace.name(), allFixtures);

        VariationFixtureWriter.write(outputPath, namespace.name(),
            VariationFixtureWriter.fromReport(report));

        printSummary(report, threshold);

        return report;
    }

    private static void printSummary(CorpusQualityReport report, double threshold) {
        System.out.printf("%n=== Corpus Quality Report: %s ===%n", report.namespace());
        System.out.printf("Overall match rate: %.1f%% (%d/%d)%n",
            report.matchRate() * 100, report.totalMatched(), report.totalVariations());
        System.out.printf("False positives: %d%n", report.totalFalsePositives());
        System.out.printf("Collisions: %d%n", report.collisions().size());

        for (TemplateReport t : report.templates()) {
            String status = t.matchRate() >= threshold ? "PASS" : "FAIL";
            System.out.printf("  [%s] %s: %.1f%% (%d/%d) fieldMismatch=%d falsePos=%d%n",
                status, t.templateName(), t.matchRate() * 100,
                t.matchedCount(), t.variationCount(),
                t.fieldMismatchCount(), t.falsePositiveCount());
        }

        if (!report.collisions().isEmpty()) {
            System.out.printf("%nCollisions:%n");
            for (var c : report.collisions()) {
                System.out.printf("  '%s' -> %s:%s%n",
                    c.variation(), c.collidingNamespace(), c.collidingTemplate());
            }
        }

        System.out.printf("%nResult: %s (threshold: %.1f%%)%n",
            report.matchRate() >= threshold ? "PASS" : "FAIL",
            threshold * 100);
    }

    public static void main(String[] args) {
        System.err.println("CLI argument parsing not yet implemented — use run() directly in tests.");
        System.exit(1);
    }
}
