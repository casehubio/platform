package io.casehub.yaml.core.orchestration;

public record StepError(String message, String exceptionClass, String stackTrace) {}
