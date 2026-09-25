package io.casehub.yaml.step.catalog;

import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.plugin.api.StepResult;
import io.casehub.yaml.step.CatalogEntry;
import io.casehub.yaml.step.CatalogSource;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class McpToolSource implements CatalogSource {

    private final Set<String> toolNames;
    private final Function<String, Map<String, Object>> toolInvoker;

    public McpToolSource(Set<String> toolNames,
                          Function<String, Map<String, Object>> toolInvoker) {
        this.toolNames = toolNames;
        this.toolInvoker = toolInvoker;
    }

    @Override
    public void populate(Map<String, CatalogEntry> entries) {
        for (String toolName : toolNames) {
            StepDefinition def = new StepDefinition(toolName, null,
                    Map.of(), Map.of(), new InvokeBinding.Mcp(toolName));

            entries.putIfAbsent(toolName, new CatalogEntry(toolName, def,
                    (params, services) -> {
                        try {
                            Map<String, Object> result = toolInvoker.apply(toolName);
                            return StepResult.of(result != null ? result : Map.of(),
                                    Map.of("tool", toolName));
                        } catch (Exception e) {
                            return StepResult.failed("MCP tool '" + toolName + "' failed: " + e.getMessage());
                        }
                    }));
        }
    }

    @Override
    public int priority() {
        return 300;
    }
}
