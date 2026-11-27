package io.casehub.template.registry;

import java.util.Objects;

public record SelectorInfo(
    String variableName,
    String function
) {
    public SelectorInfo {
        Objects.requireNonNull(variableName, "variableName");
        Objects.requireNonNull(function, "function");
    }
}
