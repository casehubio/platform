package test.plugins;

import io.casehub.yaml.plugin.api.*;

@Plugin(value = "bad-streaming-void", streaming = true)
public record StreamingVoidPlugin() {
    @Execute
    public void apply(String input) {}
}
