package test.plugins;

import io.casehub.yaml.plugin.api.*;
import java.util.Map;

@StepPlugin("test-action")
public record ValidPlugin(@Required String name, @Optional int count) {
    @Execute
    public StepResult run() {
        return StepResult.of(Map.of("name", name, "count", count));
    }
}
