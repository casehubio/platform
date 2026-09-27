package io.casehub.yaml.step.catalog;

import io.casehub.platform.mcp.DomainModel;
import io.casehub.platform.mcp.DomainModelRegistry;
import io.casehub.platform.mcp.ModelScanComplete;
import io.casehub.platform.mcp.OperationDescriptor;
import io.casehub.platform.mcp.ParameterDescriptor;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.core.step.StepParameter;
import io.casehub.yaml.core.step.StepParameterType;
import io.casehub.yaml.step.CatalogEntry;
import io.casehub.yaml.step.CatalogSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class McpStepCatalogWiring implements CatalogSource {

    @Inject
    Instance<DomainModelRegistry> registryInstance;

    @Inject
    Instance<io.casehub.platform.api.mcp.ToolDispatcher> dispatcherInstance;

    private McpToolSource delegate;

    void onScanComplete(@Observes ModelScanComplete event) {
        if (!registryInstance.isResolvable()) {
            return;
        }

        DomainModelRegistry registry = registryInstance.get();

        Map<String, StepDefinition> definitions = new LinkedHashMap<>();
        for (DomainModel domain : registry.getDomains()) {
            for (OperationDescriptor op : domain.operations()) {
                if (op.type() == OperationDescriptor.OperationType.STREAM) continue;
                String toolName = domain.name() + "_" + op.name();
                definitions.put(toolName, buildDefinition(toolName, op));
            }
        }

        if (definitions.isEmpty()) {
            return;
        }

        io.casehub.platform.api.mcp.ToolDispatcher dispatcher = resolveDispatcher();
        delegate = new McpToolSource(definitions, (toolName, params) -> {
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
    }

    private io.casehub.platform.api.mcp.ToolDispatcher resolveDispatcher() {
        return dispatcherInstance.isResolvable() ? dispatcherInstance.get() : null;
    }

    @Override
    public void populate(Map<String, CatalogEntry> entries) {
        if (delegate != null) {
            delegate.populate(entries);
        }
    }

    @Override
    public int priority() {
        return 300;
    }

    private StepDefinition buildDefinition(String toolName, OperationDescriptor op) {
        Map<String, StepParameter> inputs = new LinkedHashMap<>();
        for (ParameterDescriptor pd : op.params()) {
            inputs.put(pd.name(), new StepParameter(
                    mapType(pd.typeName()),
                    pd.required(),
                    null,
                    List.of(),
                    null,
                    pd.description()));
        }
        return new StepDefinition(toolName, op.summary(),
                inputs, Map.of(), new InvokeBinding.Mcp(toolName));
    }

    static StepParameterType mapType(String typeName) {
        if (typeName == null) return StepParameterType.STRING;
        return switch (typeName) {
            case "String" -> StepParameterType.STRING;
            case "Integer", "Long", "int", "long" -> StepParameterType.INTEGER;
            case "Double", "Float", "BigDecimal", "double", "float" -> StepParameterType.NUMBER;
            case "Boolean", "boolean" -> StepParameterType.BOOLEAN;
            default -> {
                if (typeName.startsWith("List") || typeName.startsWith("Collection"))
                    yield StepParameterType.ARRAY;
                yield StepParameterType.OBJECT;
            }
        };
    }
}
