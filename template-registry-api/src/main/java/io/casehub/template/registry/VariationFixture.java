package io.casehub.template.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record VariationFixture(
    String templateName,
    String input,
    boolean expectMatch,
    Map<String, String> expectedFields
) {
    public VariationFixture {
        Objects.requireNonNull(templateName, "templateName");
        Objects.requireNonNull(input, "input");
        expectedFields = expectedFields != null
            ? Collections.unmodifiableMap(new LinkedHashMap<>(expectedFields))
            : Map.of();
    }
}
