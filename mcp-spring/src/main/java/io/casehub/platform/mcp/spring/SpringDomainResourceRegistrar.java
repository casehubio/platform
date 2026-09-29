package io.casehub.platform.mcp.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.platform.api.mcp.McpResourceContent;
import io.casehub.platform.api.mcp.McpResourceDescriptor;
import io.casehub.platform.api.mcp.McpResourceRegistry;
import io.casehub.platform.mcp.DomainContentFormatter;
import io.casehub.platform.mcp.DomainModel;
import io.casehub.platform.mcp.DomainModelRegistry;
import io.casehub.platform.mcp.ModelScanComplete;
import org.springframework.context.event.EventListener;

public class SpringDomainResourceRegistrar {

    private static final System.Logger LOG = System.getLogger(SpringDomainResourceRegistrar.class.getName());

    private final McpResourceRegistry resourceRegistry;
    private final DomainModelRegistry domainModelRegistry;
    private final ObjectMapper mapper;

    public SpringDomainResourceRegistrar(McpResourceRegistry resourceRegistry,
                                          DomainModelRegistry domainModelRegistry) {
        this.resourceRegistry = resourceRegistry;
        this.domainModelRegistry = domainModelRegistry;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @EventListener
    public void onScanComplete(ModelScanComplete event) {
        resourceRegistry.newResource(McpResourceDescriptor.of(
                        "casehub-domain-index",
                        "casehub://domain-index",
                        "application/json",
                        "Lists all CaseHub domains with summaries and operation counts"))
                .handler(request -> {
                    String json = mapper.writeValueAsString(
                            DomainContentFormatter.formatIndex(domainModelRegistry.getDomains()));
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
                    var domain = domainModelRegistry.getDomain(domainName)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Unknown domain: " + domainName));
                    String json = mapper.writeValueAsString(
                            DomainContentFormatter.formatDomain(domain));
                    return McpResourceContent.of(request.uri(), json, "application/json");
                })
                .completion("domain", () -> domainModelRegistry.getDomains().stream()
                        .map(DomainModel::name).toList())
                .register();

        LOG.log(System.Logger.Level.INFO,
                "Registered domain metadata resources: casehub://domain-index + casehub://domains/'{domain}' ({0} domains)",
                domainModelRegistry.getDomains().size());
    }
}
