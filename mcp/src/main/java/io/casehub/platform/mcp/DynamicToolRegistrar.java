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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Set<String> activatedDomains = ConcurrentHashMap.newKeySet();


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
                           Map<String, Object> toolArgs  = args.args();
                           String              domain    = (String) toolArgs.get("domain");
                           String              operation = (String) toolArgs.get("operation");
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
                   }, true)
                   .register();

        toolManager.newTool("casehub_activate")
                   .setDescription("Activate a domain's operations as individual tools "
                                   + "for auto-completion. Use casehub_model to discover domain names.")
                   .addArgument("domain", "Domain name to activate", true, String.class)
                   .setHandler(args -> activateDomain((String) args.args().get("domain")), true)
                   .register();

        LOG.infof("Registered casehub_action with %s schema (%d domains) + casehub_activate",
                  schemaMode, registry.getDomains().size());
    }

    ToolResponse activateDomain(String domain) {
        DomainModel model = registry.getDomain(domain).orElse(null);
        if (model == null) {
            return ToolResponse.error("Unknown domain: " + domain
                                      + ". Use casehub_model to list available domains.");
        }

        if (activatedDomains.contains(domain)) {
            return ToolResponse.success("Domain '" + domain + "' already activated.");
        }

        List<String> registered = new ArrayList<>();
        try {
            for (OperationDescriptor op : model.operations()) {
                String toolName = domain + "_" + op.name();
                registerOperationTool(domain, op, toolName);
                registered.add(toolName);
            }
        } catch (Exception e) {
            for (String toolName : registered) {
                toolManager.removeTool(toolName);
            }
            LOG.warnf(e, "Failed to activate domain '%s' — rolled back %d tools",
                      domain, registered.size());
            return ToolResponse.error("Failed to activate domain '" + domain
                                      + "': " + e.getMessage());
        }

        activatedDomains.add(domain);

        LOG.infof("Activated %d tools for domain '%s': %s",
                  registered.size(), domain, registered);

        return ToolResponse.success("Activated " + registered.size()
                                    + " tools for domain '" + domain + "': " + registered);
    }

    private void registerOperationTool(String domain, OperationDescriptor op,
                                       String toolName) {
        boolean isQuery = op.type() == OperationDescriptor.OperationType.QUERY;

        ToolManager.ToolDefinition def = toolManager.newTool(toolName)
                                                    .setDescription(op.summary() != null && !op.summary().isBlank()
                                                                    ? op.summary()
                                                                    : "[" + domain + "] " + op.name());

        java.lang.reflect.Parameter[] methodParams = op.method().getParameters();
        List<ParameterDescriptor>     paramDescs   = op.params();
        for (int i = 0; i < paramDescs.size(); i++) {
            ParameterDescriptor pd = paramDescs.get(i);
            def.addArgument(pd.name(), pd.description(),
                            pd.required(), methodParams[i].getParameterizedType());
        }

        def.setAnnotations(new ToolManager.ToolAnnotations(
                null,
                isQuery,   // readOnlyHint
                false,     // destructiveHint
                isQuery,   // idempotentHint
                false      // openWorldHint
        ));

        def.setHandler(args -> {
            try {
                Object result = dispatcher.dispatch(domain, op.name(), args.args());
                return ToolResponse.success(mapper.writeValueAsString(result));
            } catch (IllegalArgumentException | IllegalStateException e) {
                return ToolResponse.error(e.getMessage());
            } catch (Exception e) {
                LOG.errorf(e, "%s dispatch failed", toolName);
                return ToolResponse.error(e.getMessage());
            }
        }, true);

        def.register();
    }

}
