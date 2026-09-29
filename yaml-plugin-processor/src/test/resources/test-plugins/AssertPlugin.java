package test.plugins;

import io.casehub.yaml.plugin.api.*;
import java.util.Map;

@Plugin(value = "assert", description = "Asserts a condition evaluates to true")
public record AssertPlugin(@Required String condition, @Optional String message) {
    @Execute
    public Result run() {
        boolean result = Boolean.parseBoolean(condition);
        if (result) {
            return Result.of(Map.of("passed", true));
        }
        String failMessage = message != null ? message : "Assertion failed: " + condition;
        return Result.failed(failMessage);
    }
}
