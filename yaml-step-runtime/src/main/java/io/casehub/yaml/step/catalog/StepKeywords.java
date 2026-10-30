package io.casehub.yaml.step.catalog;

import java.util.LinkedHashSet;
import java.util.Set;

final class StepKeywords {

    static final Set<String> DECORATOR_KEYS = Set.of(
            "if", "on-success", "on-failure", "forEach", "loop",
            "retry", "timeout", "delay", "on-error", "trigger",
            "transform", "signal", "publish", "transition",
            "semaphore", "barrier", "quorum",
            "at", "on-complete", "resource", "priority",
            "cancel", "background", "from",
            "wait", "mutex", "when");

    static final Set<String> STRUCTURAL_KEYS = Set.of(
            "step", "invoke",
            "then", "else", "match", "cases", "block",
            "try", "catch", "finally", "select",
            "parallel", "race",
            "pause", "resume");

    static final Set<String> RESERVED_KEYS;
    static {
        var combined = new LinkedHashSet<String>();
        combined.addAll(STRUCTURAL_KEYS);
        combined.addAll(DECORATOR_KEYS);
        RESERVED_KEYS = Set.copyOf(combined);
    }

    private StepKeywords() {}
}
