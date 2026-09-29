package io.casehub.yaml.step.catalog.scannertest;

import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Portability;
import io.casehub.yaml.plugin.api.Result;

import java.util.Map;

@Plugin(value = "universal-plugin", portability = Portability.UNIVERSAL)
public record UniversalPlugin(String input) {
    @Execute
    public Result run() { return Result.of(Map.of()); }
}
