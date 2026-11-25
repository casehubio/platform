package io.casehub.template.registry;

import java.util.Objects;

public record MatchTraceResult(
    TemplateMatch result,
    MatchTrace trace
) {
    public MatchTraceResult {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(trace, "trace");
    }
}
