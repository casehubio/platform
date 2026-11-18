package io.casehub.yaml.step.runner;

public record LlmFixResult(String correctedYaml, String explanation, String model) {
}
