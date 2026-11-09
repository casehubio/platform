package io.casehub.yaml.step.catalog.scannertest;

import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.Result;

import java.util.Map;

@Plugin(value = "absorbing-plugin", absorbSiblings = true)
public record AbsorbingPlugin(@Required String input) {
    @Execute
    public Result run() {
        return Result.of(Map.of("output", input));
    }
}
