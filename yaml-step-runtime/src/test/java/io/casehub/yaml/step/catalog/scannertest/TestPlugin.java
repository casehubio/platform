package io.casehub.yaml.step.catalog.scannertest;

import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.Result;

import java.util.Map;

@Plugin(value = "test-plugin", description = "A test plugin")
public record TestPlugin(@Required String name, int count) {
    @Execute
    public Result run() {
        return Result.of(Map.of("greeting", "Hello " + name));
    }
}
