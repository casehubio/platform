package io.casehub.template.registry.nlp;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateSchema;
import io.casehub.template.registry.UnifiedTemplateRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@McpDomain("template-schemas")
public class TemplateSchemaEndpoint {

    private final UnifiedTemplateRegistry registry;

    public TemplateSchemaEndpoint(UnifiedTemplateRegistry registry) {
        this.registry = registry;
    }

    @PlatformQuery("List all template schemas across all namespaces")
    public List<TemplateSchema> listSchemas(
            @ContextParam("tenancyId") String tenancyId) {
        List<TemplateSchema> all = new ArrayList<>();
        for (String ns : registry.namespaces()) {
            all.addAll(registry.schemas(ns));
        }
        return all;
    }

    @PlatformQuery("Get the schema for a specific template in a namespace")
    public Optional<TemplateSchema> getSchema(
            @ContextParam("tenancyId") String tenancyId,
            String namespace,
            String templateName) {
        return registry.schema(namespace, templateName);
    }

    @PlatformQuery("Match input text against templates in a namespace")
    public TemplateMatch matchTemplate(
            @ContextParam("tenancyId") String tenancyId,
            String namespace,
            String input) {
        return registry.match(namespace, input);
    }
}
