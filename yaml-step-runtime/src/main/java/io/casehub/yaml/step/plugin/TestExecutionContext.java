package io.casehub.yaml.step.plugin;

import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.ServiceRegistry;

import java.nio.file.Path;

public record TestExecutionContext(
        PluginRegistry pluginRegistry,
        ServiceRegistry serviceRegistry,
        Path resourceRoot,
        VariableResolver variableResolver) {}
