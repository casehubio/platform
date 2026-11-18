package io.casehub.yaml.step.runner;

public record ValidationContext(String schema, String level, boolean llmSuggestions) {
}