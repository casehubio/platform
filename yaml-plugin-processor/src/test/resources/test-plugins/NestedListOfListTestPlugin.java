package test.plugins;

import io.casehub.yaml.plugin.api.*;
import java.util.List;
import java.util.Map;

@Plugin(value = "nested-list-of-list-test", description = "Tests List of List schema generation")
public record NestedListOfListTestPlugin(
    @Required List<List<String>> matrix
) {
    @Execute
    public Result run() {
        return Result.of(Map.of());
    }
}
