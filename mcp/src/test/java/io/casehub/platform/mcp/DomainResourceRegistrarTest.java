package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.McpResourceRegistry;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class DomainResourceRegistrarTest {

    @Inject
    ResourceManager resourceManager;

    @Inject
    ResourceTemplateManager resourceTemplateManager;

    @Inject
    McpResourceRegistry mcpResourceRegistry;

    @Test
    void domainIndexResourceRegistered() {
        var resource = resourceManager.getResource("casehub://domain-index");
        assertThat(resource).isNotNull();
        assertThat(resource.mimeType()).isEqualTo("application/json");
    }

    @Test
    void domainsTemplateRegistered() {
        var template = resourceTemplateManager.getResourceTemplate("casehub-domains");
        assertThat(template).isNotNull();
        assertThat(template.uriTemplate()).isEqualTo("casehub://domains/{domain}");
    }

    @Test
    void domainIndexInRegistryList() {
        assertThat(mcpResourceRegistry.list())
                .anyMatch(d -> d.name().equals("casehub-domain-index"));
    }

    @Test
    void domainsTemplateInRegistryList() {
        assertThat(mcpResourceRegistry.list())
                .anyMatch(d -> d.name().equals("casehub-domains"));
    }
}
