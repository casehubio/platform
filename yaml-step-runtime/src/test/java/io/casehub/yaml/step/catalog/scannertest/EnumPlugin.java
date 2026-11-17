package io.casehub.yaml.step.catalog.scannertest;

import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.Result;

import java.util.Map;

@Plugin(value = "enum-plugin", description = "Plugin with enum field")
public record EnumPlugin(@Required Priority priority) {

    public enum Priority { LOW, MEDIUM, HIGH }

    @Execute
    public Result run() {
        return Result.of(Map.of("priority", priority.name()));
    }
}
