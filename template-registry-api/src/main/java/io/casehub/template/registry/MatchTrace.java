package io.casehub.template.registry;

import java.util.List;
import java.util.Objects;

public record MatchTrace(
    List<CandidateTrace> candidates,
    String bestCandidate,
    double bestScore
) {
    public MatchTrace {
        Objects.requireNonNull(candidates, "candidates");
        candidates = List.copyOf(candidates);
    }
}
