package io.casehub.platform.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Map;

@ApplicationScoped
public class DynamicToolRegistrar {

    private static final Logger LOG = Logger.getLogger(DynamicToolRegistrar.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Inject
    ToolManager toolManager;

    @Inject
    ModelRegistry registry;

    @Inject
    ReflectiveOperationDispatcher dispatcher;

    @ConfigProperty(name = "casehub.mcp.schema-mode", defaultValue = "simple")
    SchemaMode schemaMode;

    private final ObjectMapper mapper;

    public DynamicToolRegistrar() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    void onScanComplete(@Observes ModelScanComplete event) {
        Map<String, Object> schema = new McpSchemaBuilder()
                .build(schemaMode, registry.getDomains());

        toolManager.newTool("casehub_action")
                .setDescription("Execute a CaseHub operation. "
                        + "Use casehub_model first to discover available operations.")
                .setInputSchema(schema)
                .setHandler(args -> {
                    try {
                        Map<String, Object> toolArgs = args.args();
                        String domain = (String) toolArgs.get("domain");
                        String operation = (String) toolArgs.get("operation");
                        String params = toolArgs.get("params") != null
                                ? toolArgs.get("params").toString() : null;

                        Map<String, Object> paramMap = Map.of();
                        if (params != null && !params.isBlank()) {
                            paramMap = mapper.readValue(params, MAP_TYPE);
                        }
                        Object result = dispatcher.dispatch(domain, operation, paramMap);
                        return ToolResponse.success(mapper.writeValueAsString(result));
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        return ToolResponse.error(e.getMessage());
                    } catch (Exception e) {
                        LOG.errorf(e, "casehub_action dispatch failed");
                        return ToolResponse.error(e.getMessage());
                    }
                })
                .register();

        LOG.infof("Registered casehub_action with %s schema (%d domains)",
                schemaMode, registry.getDomains().size());
    }
}
