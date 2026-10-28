package io.casehub.platform.api.governance;

import java.util.Map;

public sealed interface TriageDecision {

    record Retry(Map<String, Object> modifications) implements TriageDecision {
        public Retry {
            modifications = modifications != null ? Map.copyOf(modifications) : Map.of();
        }
    }

    record Abandon(String reason) implements TriageDecision {
        public Abandon {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("abandon reason required");
            }
        }
    }
}
