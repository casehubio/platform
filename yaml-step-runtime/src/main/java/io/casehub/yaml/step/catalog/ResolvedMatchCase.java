package io.casehub.yaml.step.catalog;

import io.casehub.yaml.core.step.MatchPattern;

import java.util.List;

public record ResolvedMatchCase(
        MatchPattern pattern,
        String guard,
        List<ResolvedStep> steps) {

    public ResolvedMatchCase {
        steps = List.copyOf(steps);
    }
}
