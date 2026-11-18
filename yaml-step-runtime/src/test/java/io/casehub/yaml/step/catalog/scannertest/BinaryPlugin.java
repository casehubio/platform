package io.casehub.yaml.step.catalog.scannertest;

import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Optional;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.Result;

import java.util.Map;

@Plugin(value = "binary-plugin", description = "Plugin with byte[] fields")
public record BinaryPlugin(@Required byte[] data, @Optional byte[] extra) {
    @Execute
    public Result run() {
        return Result.of(Map.of("size", data.length + (extra != null ? extra.length : 0)));
    }
}
