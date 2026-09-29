package test.plugins;

import io.casehub.yaml.plugin.api.*;

@Plugin("missing-execute")
public record MissingExecutePlugin(@Required String name) {
}
