package test.plugins;

import io.casehub.yaml.plugin.api.*;
import java.util.Map;

@Plugin("test-action")
public record ValidPlugin(@Required String name, @Optional int count) {
    @Execute
    public Result run() {
        return Result.of(Map.of("name", name, "count", count));
    }
}
