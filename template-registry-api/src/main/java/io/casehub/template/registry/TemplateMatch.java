package io.casehub.template.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public sealed interface TemplateMatch {

    record Matched(
        String namespace,
        String templateName,
        int patternIndex,
        Map<String, String> fields,
        double score
    ) implements TemplateMatch {
        public Matched {
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(templateName, "templateName");
            Objects.requireNonNull(fields, "fields");
            if (score <= 0.0 || score > 1.0) {
                throw new IllegalArgumentException(
                    "Score must be in (0, 1.0]: " + score);
            }
            fields = Collections.unmodifiableMap(
                new LinkedHashMap<>(fields));
        }
    }

    record NearMiss(
        String namespace,
        String input,
        String suggestedTemplate,
        String suggestedFormat
    ) implements TemplateMatch {
        public NearMiss {
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(suggestedTemplate,
                "suggestedTemplate");
            Objects.requireNonNull(suggestedFormat,
                "suggestedFormat");
        }
    }

    record Unmatched(String input) implements TemplateMatch {
        public Unmatched {
            Objects.requireNonNull(input, "input");
        }
    }
}
