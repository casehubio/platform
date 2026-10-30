package test.plugins;

import io.casehub.yaml.plugin.api.*;

@Plugin(value = "uppercase-stream", streaming = true)
public record StreamingPlugin() {
    @Execute
    public String apply(String input) {
        return input.toUpperCase();
    }
}
