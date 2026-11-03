package test.plugins;

import io.casehub.yaml.plugin.api.*;
import java.util.Map;

@Plugin("expression-test")
public record ExpressionPlugin(
        @Required @Expression Object subject,
        String message
) {
    @Execute
    public Result run() {
        return Result.of(Map.of("evaluated", subject));
    }
}
