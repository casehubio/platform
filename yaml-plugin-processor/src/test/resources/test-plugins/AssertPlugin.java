package test.plugins;

import io.casehub.yaml.plugin.api.*;
import java.util.Map;

@StepPlugin(value = "assert", description = "Asserts a condition evaluates to true")
public record AssertPlugin(@Required String condition, @Optional String message) {
    @Execute
    public StepResult run() {
        boolean result = Boolean.parseBoolean(condition);
        if (result) {
            return StepResult.of(Map.of("passed", true));
        }
        String failMessage = message != null ? message : "Assertion failed: " + condition;
        return StepResult.failed(failMessage);
    }
}
