package io.casehub.yaml.step.catalog;

import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.CatalogEntry;
import io.casehub.yaml.step.CatalogSource;
import io.casehub.yaml.core.step.StepDefinition;

import java.util.Map;
import java.util.function.BiFunction;

public class McpToolSource implements CatalogSource {

    private final Map<String, StepDefinition> definitions;
    private final BiFunction<String, Map<String, Object>, Map<String, Object>> toolInvoker;

    public McpToolSource(
            Map<String, StepDefinition> definitions,
            BiFunction<String, Map<String, Object>, Map<String, Object>> toolInvoker) {
        this.definitions = Map.copyOf(definitions);
        this.toolInvoker = toolInvoker;
    }

    @Override
    public void populate(Map<String, CatalogEntry> entries) {
        for (var e : definitions.entrySet()) {
            String toolName = e.getKey();
            StepDefinition def = e.getValue();

            entries.putIfAbsent(toolName, new CatalogEntry(toolName, def,
                    (params, services) -> {
                        try {
                            Map<String, Object> result = toolInvoker.apply(toolName, params);
                            return Result.of(
                                    result != null ? result : Map.of(),
                                    Map.of("tool", toolName));
                        } catch (Exception ex) {
                            return Result.failed(
                                    "MCP tool '" + toolName + "' failed: " + ex.getMessage());
                        }
                    }));
        }
    }

    @Override
    public int priority() {
        return 300;
    }
}
