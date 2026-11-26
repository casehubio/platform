package io.casehub.yaml.step.runner;

import java.util.Set;

public record RecoveryContext(
        int stepIndex,
        String stepName,
        int currentDepth,
        int maxDepth,
        Set<String> recoveryChain) {

    public RecoveryContext {
        recoveryChain = recoveryChain != null ? Set.copyOf(recoveryChain) : Set.of();
    }
}
