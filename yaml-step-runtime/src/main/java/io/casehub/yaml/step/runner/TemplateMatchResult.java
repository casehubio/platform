package io.casehub.yaml.step.runner;

import java.util.Objects;

public sealed interface TemplateMatchResult {

    record Matched(TemplateMatchEvent event) implements TemplateMatchResult {
        public Matched {
            Objects.requireNonNull(event, "event");
        }
    }

    record NearMiss(String input, String suggestedTemplate, String suggestedFormat) implements TemplateMatchResult {
        public NearMiss {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(suggestedTemplate, "suggestedTemplate");
            Objects.requireNonNull(suggestedFormat, "suggestedFormat");
        }
    }

    record PassThrough(String input) implements TemplateMatchResult {
        public PassThrough {
            Objects.requireNonNull(input, "input");
        }
    }
}
