package io.casehub.template.registry;

import java.util.Objects;

public record VariationResult(
    String input,
    boolean expectedMatch,
    TemplateMatch actualResult,
    boolean passed
) {
    public VariationResult {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(actualResult, "actualResult");
    }
}
