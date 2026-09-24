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

    @Tool(description = "Navigate the CaseHub operation catalog as a tree. "
                        + "Call with no path for root nodes. "
                        + "Call with a path to explore deeper — returns child nodes or operations at leaves.")
    public String casehub_model(
            @ToolArg(description = "Path to navigate (e.g. 'clinical', 'clinical/trials'). Omit for root.", required = false)
            String path) throws JsonProcessingException {
        if (path != null && !path.isBlank()) {
            DomainModel exact = registry.getDomain(path).orElse(null);
            if (exact != null) {
                var children = registry.listChildren(path);
                if (children.size() <= 1) {
                    return mapper.writeValueAsString(DomainContentFormatter.formatDomain(exact));
                }
            }

            var children = registry.listChildren(path);
            if (!children.isEmpty()) {
                return mapper.writeValueAsString(DomainContentFormatter.formatTreeNodes(path, children));
            }

            throw new IllegalArgumentException("Unknown path: " + path
                    + ". Use casehub_model() to see available paths or casehub_search() to find by keyword.");
        }

        var roots = registry.listChildren("");
        return mapper.writeValueAsString(DomainContentFormatter.formatTreeNodes(null, roots));
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
