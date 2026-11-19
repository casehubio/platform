package test.plugins;

import io.casehub.yaml.plugin.api.*;
import java.util.List;
import java.util.Map;

@Plugin(value = "nested-list-test", description = "Tests nested List schema generation")
public record NestedListTestPlugin(
    @Required List<Outer> items
) {
    public record Outer(String name, List<String> tags) {}

    @Execute
    public Result run() {
        return Result.of(Map.of());
    }
}
