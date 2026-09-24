package test.plugins;

import io.casehub.yaml.plugin.api.*;

@StepPlugin("missing-execute")
public record MissingExecutePlugin(@Required String name) {
}
