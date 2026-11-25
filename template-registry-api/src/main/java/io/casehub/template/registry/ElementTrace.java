package io.casehub.template.registry;

import java.util.Objects;

public record ElementTrace(
    ElementKind kind,
    String expected,
    String actual,
    boolean matched
) {
    public ElementTrace {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(expected, "expected");
    }
}
