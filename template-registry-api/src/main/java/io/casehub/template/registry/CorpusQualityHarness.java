package io.casehub.template.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CorpusQualityHarness {

    private final UnifiedTemplateRegistry registry;

    public CorpusQualityHarness(UnifiedTemplateRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public CorpusQualityReport evaluate(String namespaceName,
                                        List<VariationFixture> fixtures) {
        Objects.requireNonNull(namespaceName, "namespaceName");
        Objects.requireNonNull(fixtures, "fixtures");

        Map<String, List<VariationResult>> resultsByTemplate  = new LinkedHashMap<>();
        Map<String, int[]>                 countersByTemplate = new LinkedHashMap<>();
        List<CollisionDetector.Collision>  allCollisions      = new ArrayList<>();

        int totalVariations     = 0;
        int totalMatched        = 0;
        int totalFalsePositives = 0;

        for (VariationFixture fixture : fixtures) {
            TemplateMatch match = registry.match(namespaceName, fixture.input());
            boolean isCorrectMatch = match instanceof TemplateMatch.Matched m
                                     && m.templateName().equals(fixture.templateName());
            boolean fieldsMatch = true;

            if (isCorrectMatch && fixture.expectMatch() && !fixture.expectedFields().isEmpty()) {
                var matched = (TemplateMatch.Matched) match;
                fieldsMatch = fixture.expectedFields().entrySet().stream()
                                     .allMatch(e -> e.getValue().equals(matched.fields().get(e.getKey())));
            }

            boolean passed;
            if (fixture.expectMatch()) {
                passed = isCorrectMatch && fieldsMatch;
            } else {
                passed = !isCorrectMatch;
            }

            var result = new VariationResult(fixture.input(), fixture.expectMatch(), match, passed);

            resultsByTemplate
                    .computeIfAbsent(fixture.templateName(), k -> new ArrayList<>())
                    .add(result);

            int[] counters = countersByTemplate
                                     .computeIfAbsent(fixture.templateName(), k -> new int[4]);

            if (fixture.expectMatch()) {
                counters[0]++;
                totalVariations++;
                if (isCorrectMatch) {
                    counters[1]++;
                    totalMatched++;
                    if (!fieldsMatch) {
                        counters[2]++;
                    }
                }
            } else {
                if (isCorrectMatch) {
                    counters[3]++;
                    totalFalsePositives++;
                }
            }

            if (fixture.expectMatch()) {
                for (String otherNs : registry.namespaces()) {
                    if (otherNs.equals(namespaceName)) {continue;}
                    TemplateMatch crossMatch = registry.match(otherNs, fixture.input());
                    if (crossMatch instanceof TemplateMatch.Matched m) {
                        allCollisions.add(new CollisionDetector.Collision(
                                fixture.input(), m.namespace(), m.templateName()));
                    }
                }
            }
        }

        List<TemplateReport> templateReports = new ArrayList<>();
        for (var entry : resultsByTemplate.entrySet()) {
            int[] c = countersByTemplate.get(entry.getKey());
            templateReports.add(new TemplateReport(
                    entry.getKey(), c[0], c[1], c[2], c[3], entry.getValue()));
        }

        return new CorpusQualityReport(
                namespaceName, templateReports, allCollisions,
                totalVariations, totalMatched, totalFalsePositives);
    }
}
