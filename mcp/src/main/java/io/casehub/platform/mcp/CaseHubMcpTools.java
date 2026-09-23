package io.casehub.platform.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.WrapBusinessError;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@McpServer("casehub")
@WrapBusinessError({IllegalArgumentException.class, IllegalStateException.class})
@ApplicationScoped
public class CaseHubMcpTools {

    @Inject
    DomainModelRegistry registry;

    private final ObjectMapper mapper;

    public CaseHubMcpTools() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Tool(description = "Navigate the CaseHub operation catalog. "
                        + "Call without arguments for an app list. "
                        + "Call with app for that app's domains. "
                        + "Call with domain for operation details.")
    public String casehub_model(
            @ToolArg(description = "Domain name to drill into (omit for app/domain list)")
            String domain,
            @ToolArg(description = "App name to list domains for (omit for app list)")
            String app) throws JsonProcessingException {
        if (domain != null && !domain.isBlank()) {
            DomainModel domainModel = registry.getDomain(domain)
                                              .orElseThrow(() -> new IllegalArgumentException("Unknown domain: " + domain));
            return mapper.writeValueAsString(DomainContentFormatter.formatDomain(domainModel));
        }
        if (app != null && !app.isBlank()) {
            List<DomainModel> appDomains = registry.getDomainsByApp(app);
            if (appDomains.isEmpty()) {
                throw new IllegalArgumentException("Unknown app: " + app);
            }
            return mapper.writeValueAsString(DomainContentFormatter.formatAppDomains(app, appDomains));
        }
        return mapper.writeValueAsString(
                DomainContentFormatter.formatAppIndex(registry.getApps(), registry.getDomains()));
    }

    @Tool(description = "Search CaseHub operations by keyword. "
                        + "Matches operation names, summaries, parameter names, and return types "
                        + "across all domains.")
    public String casehub_search(
            @ToolArg(description = "Search query (case-insensitive substring match)")
            String query) throws JsonProcessingException {
        List<SearchResult> results = registry.search(query);
        return mapper.writeValueAsString(DomainContentFormatter.formatSearchResults(query, results));
    }


}
