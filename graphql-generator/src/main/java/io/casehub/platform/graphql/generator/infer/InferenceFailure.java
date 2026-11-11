package io.casehub.platform.graphql.generator.infer;

public enum InferenceFailure {
    NO_METHOD_BODY,
    VAR_TYPED_SPI,
    MULTIPLE_RETURNS,
    INCOMPLETE_CAPABILITY_CHAIN,
    NO_SPI_VARIABLE,
    UNRECOGNIZED_PATTERN
}
