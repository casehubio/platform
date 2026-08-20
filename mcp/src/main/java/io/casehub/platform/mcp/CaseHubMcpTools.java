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

@McpServer("casehub")
@WrapBusinessError({IllegalArgumentException.class, IllegalStateException.class})
@ApplicationScoped
public class CaseHubMcpTools {

    @Inject
    ModelRegistry registry;

    private final ObjectMapper mapper;

    public CaseHubMcpTools() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Tool(description = "Navigate the CaseHub operation catalog. "
            + "Call without domain for a domain list. "
            + "Call with domain for operation details.")
    public String casehub_model(
            @ToolArg(description = "Domain name to drill into (omit for domain list)")
            String domain) throws JsonProcessingException {
        if (domain == null || domain.isBlank()) {
            return mapper.writeValueAsString(DomainContentFormatter.formatIndex(registry.getDomains()));
        }
        DomainModel domainModel = registry.getDomain(domain)
                                          .orElseThrow(() -> new IllegalArgumentException("Unknown domain: " + domain));
        return mapper.writeValueAsString(DomainContentFormatter.formatDomain(domainModel));
    }

}
