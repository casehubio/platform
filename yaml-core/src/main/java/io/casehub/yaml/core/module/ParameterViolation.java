package io.casehub.yaml.core.module;

public record ParameterViolation(String parameterName, String constraint,
                                  String message, Object actualValue,
                                  String technicalDetail) {}
