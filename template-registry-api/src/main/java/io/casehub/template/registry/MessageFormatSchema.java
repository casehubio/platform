package io.casehub.template.registry;

import java.util.List;
import java.util.Objects;

public record MessageFormatSchema(
    String pattern,
    List<SelectorInfo> selectors,
    List<String> variantKeys
) {
    public MessageFormatSchema {
        Objects.requireNonNull(pattern, "pattern");
        selectors = selectors != null ? List.copyOf(selectors) : List.of();
        variantKeys = variantKeys != null ? List.copyOf(variantKeys) : List.of();
    }
}
