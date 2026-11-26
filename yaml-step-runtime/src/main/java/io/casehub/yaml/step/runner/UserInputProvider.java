package io.casehub.yaml.step.runner;

import java.util.Map;

@FunctionalInterface
public interface UserInputProvider {
    String prompt(String promptText, Map<String, Object> context);
}
