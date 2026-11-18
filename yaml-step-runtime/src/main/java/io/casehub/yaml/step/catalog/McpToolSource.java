package io.casehub.yaml.step.catalog;

import io.casehub.yaml.core.step.Declaration;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.Portability;
import io.casehub.yaml.plugin.api.Result;

import java.util.Map;
import java.util.function.BiFunction;

public class McpToolSource {

    private final Map<String, Declaration>                                     declarations;
    private final Map<String, String>                                          actionDomains;
    private final BiFunction<String, Map<String, Object>, Map<String, Object>> toolInvoker;

    public McpToolSource(
            Map<String, Declaration> declarations,
            BiFunction<String, Map<String, Object>, Map<String, Object>> toolInvoker) {
        this(declarations, Map.of(), toolInvoker);
    }

    public McpToolSource(
            Map<String, Declaration> declarations,
            Map<String, String> actionDomains,
            BiFunction<String, Map<String, Object>, Map<String, Object>> toolInvoker) {
        this.declarations  = Map.copyOf(declarations);
        this.actionDomains = Map.copyOf(actionDomains);
        this.toolInvoker   = toolInvoker;
    }

    public void populate(PluginRegistry registry) {
        for (var e : declarations.entrySet()) {
            String      toolName = e.getKey();
            Declaration decl     = e.getValue();
            String      domain   = actionDomains.get(toolName);

            registry.register(new Definition(toolName, decl.description(),
                                             decl.inputs(), decl.outputs(), Portability.UNIVERSAL,
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
                                             }, "mcp-invoke", java.util.Optional.empty(), false, Map.of(), domain));
        }
    }
}
