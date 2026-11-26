package io.casehub.template.registry;

import java.util.List;
import java.util.Objects;

public record TemplateReport(
    String templateName,
    int variationCount,
    int matchedCount,
    int fieldMismatchCount,
    int falsePositiveCount,
    List<VariationResult> variations
) {
    public TemplateReport {
        Objects.requireNonNull(templateName, "templateName");
        Objects.requireNonNull(variations, "variations");
        variations = List.copyOf(variations);
    }

    public double matchRate() {
        return variationCount == 0 ? 1.0 : (double) matchedCount / variationCount;
    }
}
