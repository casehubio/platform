package test.plugins;

import io.casehub.yaml.plugin.api.*;
import java.util.List;
import java.util.Map;

@Plugin(value = "list-test", description = "Tests List schema generation")
public record ListTestPlugin(
    @Required List<String> tags,
    @Optional List<TextMark> marks
) {
    public record TextMark(String id, int charOffset) {}

    @Execute
    public Result run() {
        return Result.of(Map.of());
    }
}
