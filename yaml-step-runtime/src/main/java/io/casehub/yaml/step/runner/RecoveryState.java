package io.casehub.yaml.step.runner;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record RecoveryState(int depth, int maxDepth, Set<String> chain) {

    public RecoveryState {
        chain = chain != null ? Collections.unmodifiableSet(new LinkedHashSet<>(chain)) : Set.of();
    }

    public static RecoveryState initial(int maxDepth) {
        return new RecoveryState(0, maxDepth, Set.of());
    }

    public RecoveryState descend(String playbookPath) {
        var expanded = new LinkedHashSet<>(chain);
        expanded.add(playbookPath);
        return new RecoveryState(depth + 1, maxDepth, Collections.unmodifiableSet(expanded));
    }

    public boolean isAtLimit() {
        return depth >= maxDepth;
    }

    public boolean wouldCycle(String path) {
        return chain.contains(path);
    }
}
