package io.casehub.yaml.step.catalog.scannertest;

import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Result;

import java.util.Map;

@Plugin("camel-test")
public record CamelCasePlugin(String firstName, int maxRetries) {
    @Execute
    public Result run() { return Result.of(Map.of()); }
}
