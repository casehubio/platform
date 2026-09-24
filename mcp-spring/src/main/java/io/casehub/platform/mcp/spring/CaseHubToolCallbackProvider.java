package io.casehub.platform.mcp.spring;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.platform.mcp.DomainContentFormatter;
import io.casehub.platform.mcp.DomainModel;
import io.casehub.platform.mcp.DomainModelRegistry;
import io.casehub.platform.mcp.McpSchemaBuilder;
import io.casehub.platform.mcp.OperationDescriptor;
import io.casehub.platform.mcp.SchemaMode;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CaseHubToolCallbackProvider implements ToolCallbackProvider {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DomainModelRegistry registry;
    private final SpringOperationDispatcher dispatcher;
    private final ObjectMapper mapper;

    public CaseHubToolCallbackProvider(DomainModelRegistry registry,
                                        SpringOperationDispatcher dispatcher) {
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        List<ToolCallback> tools = new ArrayList<>();

        tools.add(buildModelTool());
        tools.add(buildSearchTool());
        tools.add(buildActionTool());

        for (DomainModel domain : registry.getDomains()) {
            for (OperationDescriptor op : domain.operations()) {
                tools.add(buildOperationTool(domain.name(), op));
            }
        }

        return tools.toArray(ToolCallback[]::new);
    }

    private ToolCallback buildModelTool() {
        List<String> apps = registry.getApps();
        boolean hasApps = apps.stream().anyMatch(a -> !a.isEmpty());

        return FunctionToolCallback.builder("casehub_model", (String input) -> {
                    try {
                        if (input != null && !input.isBlank()) {
                            String app = input.strip().replace("\"", "");
                            var appDomains = registry.getDomainsByApp(app);
                            if (!appDomains.isEmpty()) {
                                return mapper.writeValueAsString(
                                        DomainContentFormatter.formatAppDomains(app, appDomains));
                            }
                            var domain = registry.getDomain(app);
                            if (domain.isPresent()) {
                                return mapper.writeValueAsString(
                                        DomainContentFormatter.formatDomain(domain.get()));
                            }
                        }
                        if (hasApps) {
                            return mapper.writeValueAsString(
                                    DomainContentFormatter.formatAppIndex(apps, registry.getDomains(),
                                            registry.getAppCapabilities()));
                        }
                        return mapper.writeValueAsString(
                                DomainContentFormatter.formatIndex(registry.getDomains()));
                    } catch (Exception e) {
                        return "{\"error\": \"" + e.getMessage() + "\"}";
                    }
                })
                .description("Browse the CaseHub operation catalog. "
                        + "Call with no args for the app index, an app name for its domains, "
                        + "or a domain name for its operations.")
                .inputType(String.class)
                .build();
    }

    private ToolCallback buildSearchTool() {
        return FunctionToolCallback.builder("casehub_search", (String input) -> {
                    try {
                        String query = input != null ? input.strip().replace("\"", "") : "";
                        var results = registry.search(query);
                        return mapper.writeValueAsString(
                                DomainContentFormatter.formatSearchResults(query, results));
                    } catch (Exception e) {
                        return "{\"error\": \"" + e.getMessage() + "\"}";
                    }
                })
                .description("Search CaseHub operations by keyword. "
                        + "Matches operation names, summaries, parameter names, return types, "
                        + "app names, capabilities, and tags.")
                .inputType(String.class)
                .build();
    }

    private ToolCallback buildActionTool() {
        return FunctionToolCallback.builder("casehub_action", (String input) -> {
                    try {
                        Map<String, Object> args = mapper.readValue(input, MAP_TYPE);
                        String domain = (String) args.get("domain");
                        String operation = (String) args.get("operation");
                        String params = args.get("params") != null
                                ? args.get("params").toString() : null;

                        Map<String, Object> paramMap = Map.of();
                        if (params != null && !params.isBlank()) {
                            paramMap = mapper.readValue(params, MAP_TYPE);
                        }
                        Object result = dispatcher.dispatch(domain, operation, paramMap);
                        return mapper.writeValueAsString(result);
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        return "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
                    } catch (Exception e) {
                        return "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
                    }
                })
                .description("Execute a CaseHub operation. "
                        + "Use casehub_model to browse or casehub_search to find operations by keyword. "
                        + "Input: JSON with 'domain', 'operation', and optional 'params'.")
                .inputType(String.class)
                .build();
    }

    private ToolCallback buildOperationTool(String domain, OperationDescriptor op) {
        String toolName = domain.replace("-", "_") + "_" + op.name();
        String description = op.summary() != null && !op.summary().isBlank()
                ? op.summary()
                : "[" + domain + "] " + op.name();

        return FunctionToolCallback.builder(toolName, (String input) -> {
                    try {
                        Map<String, Object> params = input != null && !input.isBlank()
                                ? mapper.readValue(input, MAP_TYPE) : Map.of();
                        Object result = dispatcher.dispatch(domain, op.name(), params);
                        return mapper.writeValueAsString(result);
                    } catch (Exception e) {
                        return "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
                    }
                })
                .description(description)
                .inputType(String.class)
                .build();
    }
}
