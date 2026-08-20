package io.casehub.platform.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.platform.api.mcp.McpResourceContent;
import io.casehub.platform.api.mcp.McpResourceDescriptor;
import io.casehub.platform.api.mcp.McpResourceRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DomainResourceRegistrar {

    private static final Logger LOG = Logger.getLogger(DomainResourceRegistrar.class);

    @Inject
    McpResourceRegistry resourceRegistry;

    @Inject
    ModelRegistry modelRegistry;

    private final ObjectMapper mapper;

    public DomainResourceRegistrar() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    void onScanComplete(@Observes ModelScanComplete event) {
        resourceRegistry.newResource(McpResourceDescriptor.of(
                        "casehub-domain-index",
                        "casehub://domain-index",
                        "application/json",
                        "Lists all CaseHub domains with summaries and operation counts"))
                .handler(request -> {
                    String json = mapper.writeValueAsString(
                            DomainContentFormatter.formatIndex(modelRegistry.getDomains()));
                    return McpResourceContent.of(request.uri(), json, "application/json");
                })
                .register();

        resourceRegistry.newResource(McpResourceDescriptor.template(
                        "casehub-domains",
                        "casehub://domains/{domain}",
                        "application/json",
                        "Domain detail: operations, params, state, events"))
                .handler(request -> {
                    String domainName = request.templateArgs().get("domain");
                    var domain = modelRegistry.getDomain(domainName)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Unknown domain: " + domainName));
                    String json = mapper.writeValueAsString(
                            DomainContentFormatter.formatDomain(domain));
                    return McpResourceContent.of(request.uri(), json, "application/json");
                })
                .completion("domain", () -> modelRegistry.getDomains().stream()
                        .map(DomainModel::name).toList())
                .register();

        LOG.infof("Registered domain metadata resources: casehub://domain-index + casehub://domains/{domain} (%d domains)",
                modelRegistry.getDomains().size());
    }
}
