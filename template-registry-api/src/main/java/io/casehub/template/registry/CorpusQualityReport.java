package io.casehub.template.registry;

import java.util.List;
import java.util.Objects;

public record CorpusQualityReport(
    String namespace,
    List<TemplateReport> templates,
    List<CollisionDetector.Collision> collisions,
    int totalVariations,
    int totalMatched,
    int totalFalsePositives
) {
    public CorpusQualityReport {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(templates, "templates");
        Objects.requireNonNull(collisions, "collisions");
        templates = List.copyOf(templates);
        collisions = List.copyOf(collisions);
    }

    public double matchRate() {
        return totalVariations == 0 ? 1.0 : (double) totalMatched / totalVariations;
    }
}
