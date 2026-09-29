package io.casehub.yaml.step.catalog;

import io.casehub.platform.mcp.DomainModel;
import io.casehub.platform.mcp.DomainModelRegistry;
import io.casehub.platform.mcp.ModelScanComplete;
import io.casehub.platform.mcp.OperationDescriptor;
import io.casehub.platform.mcp.ParameterDescriptor;
import io.casehub.yaml.core.step.Declaration;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.plugin.api.Parameter;
import io.casehub.yaml.plugin.api.ParameterType;
import io.casehub.yaml.plugin.api.PluginRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class McpStepCatalogWiring {

    @Inject
    Instance<DomainModelRegistry> registryInstance;

    @Inject
    Instance<io.casehub.platform.api.mcp.ToolDispatcher> dispatcherInstance;

    @Inject
    Instance<PluginRegistry> pluginRegistryInstance;

    private McpToolSource delegate;

    void onScanComplete(@Observes ModelScanComplete event) {
        if (!registryInstance.isResolvable()) {
            return;
        }

        DomainModelRegistry registry = registryInstance.get();

        Map<String, Declaration> declarations = new LinkedHashMap<>();
        for (DomainModel domain : registry.getDomains()) {
            for (OperationDescriptor op : domain.operations()) {
                if (op.type() == OperationDescriptor.OperationType.STREAM) continue;
                String toolName = domain.name() + "_" + op.name();
                declarations.put(toolName, buildDeclaration(toolName, op));
            }
        }

        if (declarations.isEmpty()) {
            return;
        }

        io.casehub.platform.api.mcp.ToolDispatcher dispatcher = resolveDispatcher();
        delegate = new McpToolSource(declarations, (toolName, params) -> {
            if (dispatcher == null) {
                return Map.of("error", "ToolDispatcher not available");
            }
            try {
                String[] parts = toolName.split("_", 2);
                Object result = dispatcher.dispatch(parts[0], parts[1], params);
                if (result instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) result;
                    return map;
                }
                return Map.of("result", result != null ? result : "null");
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw new RuntimeException("MCP dispatch failed for " + toolName + ": " + cause.getMessage(), cause);
            }
        });

        if (pluginRegistryInstance.isResolvable()) {
            delegate.populate(pluginRegistryInstance.get());
        }
    }

    private io.casehub.platform.api.mcp.ToolDispatcher resolveDispatcher() {
        return dispatcherInstance.isResolvable() ? dispatcherInstance.get() : null;
    }

    private Declaration buildDeclaration(String toolName, OperationDescriptor op) {
        Map<String, Parameter> inputs = new LinkedHashMap<>();
        for (ParameterDescriptor pd : op.params()) {
            inputs.put(pd.name(), new Parameter(
                    mapType(pd.typeName()),
                    pd.required(),
                    null,
                    List.of(),
                    null,
                    pd.description()));
        }
        return new Declaration(toolName, op.summary(),
                inputs, Map.of(), new InvokeBinding.Mcp(toolName));
    }

    static ParameterType mapType(String typeName) {
        if (typeName == null) return ParameterType.STRING;
        return switch (typeName) {
            case "String" -> ParameterType.STRING;
            case "Integer", "Long", "int", "long" -> ParameterType.INTEGER;
            case "Double", "Float", "BigDecimal", "double", "float" -> ParameterType.NUMBER;
            case "Boolean", "boolean" -> ParameterType.BOOLEAN;
            default -> {
                if (typeName.startsWith("List") || typeName.startsWith("Collection"))
                    yield ParameterType.ARRAY;
                yield ParameterType.OBJECT;
            }
        };
    }
}
