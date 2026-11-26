package io.casehub.yaml.step.runner;

import java.util.Map;

public sealed interface RecoveryAction {
    record RunPlaybook(String playbookPath, Map<String, Object> vars) implements RecoveryAction {
        public RunPlaybook {
            vars = vars != null ? Map.copyOf(vars) : Map.of();
        }
    }

    record Resolved(String explanation) implements RecoveryAction {}

    record Unrecoverable(String reason, Map<String, Object> enrichedDetail) implements RecoveryAction {
        public Unrecoverable {
            enrichedDetail = enrichedDetail != null ? Map.copyOf(enrichedDetail) : Map.of();
        }
    }

    record PromptUser(String promptText, String variableName,
                      String capability, Map<String, Object> metadata)
            implements RecoveryAction {
        public PromptUser {
            metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        }
    }

}
