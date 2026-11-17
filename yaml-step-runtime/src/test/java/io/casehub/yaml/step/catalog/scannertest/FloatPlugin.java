package io.casehub.yaml.step.catalog.scannertest;

import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Result;

import java.util.Map;

@Plugin(value = "float-plugin", description = "Plugin with float/double fields")
public record FloatPlugin(double value) {
    @Execute
    public Result run() {
        return Result.of(Map.of("value", value));
    }
}
