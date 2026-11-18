package io.casehub.yaml.step.runner;

public record ValidationSuggestion(int diagnosticIndex, String suggestion, String exampleYaml) {
}