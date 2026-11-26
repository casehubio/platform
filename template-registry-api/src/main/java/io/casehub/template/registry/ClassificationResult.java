package io.casehub.template.registry;

import java.util.Map;
import java.util.Objects;

public sealed interface ClassificationResult {

    record Matched(
        String templateName,
        Map<String, String> fields,
        String suggestedVariation,
        double confidence
    ) implements ClassificationResult {
        public Matched {
            Objects.requireNonNull(templateName, "templateName");
            Objects.requireNonNull(fields, "fields");
            Objects.requireNonNull(suggestedVariation, "suggestedVariation");
            if (confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException(
                    "Confidence must be in [0.0, 1.0]: " + confidence);
            }
            fields = Map.copyOf(fields);
        }
    }

    record Unmatched() implements ClassificationResult {}
}
