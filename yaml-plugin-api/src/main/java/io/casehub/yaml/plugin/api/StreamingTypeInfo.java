package io.casehub.yaml.plugin.api;

import java.util.Objects;

public record StreamingTypeInfo(
    Class<?> inputType,
    Class<?> outputType,
    String inputKey,
    StreamingStepFactory<?, ?> factory
) {
    public StreamingTypeInfo {
        Objects.requireNonNull(inputType, "inputType");
        Objects.requireNonNull(outputType, "outputType");
        Objects.requireNonNull(inputKey, "inputKey");
        Objects.requireNonNull(factory, "factory");
    }
}
