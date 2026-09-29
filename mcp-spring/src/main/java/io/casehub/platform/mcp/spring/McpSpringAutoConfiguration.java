package io.casehub.platform.mcp.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.platform.api.mcp.McpResourceRegistry;
import io.casehub.platform.mcp.DomainModelRegistry;
import io.modelcontextprotocol.server.McpSyncServer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = "org.springframework.ai.autoconfigure.mcp.server.McpServerAutoConfiguration")
public class McpSpringAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DomainModelRegistry domainModelRegistry() {
        return new DomainModelRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    SpringModelScanner springModelScanner(ApplicationContext context,
                                           DomainModelRegistry registry,
                                           ApplicationEventPublisher eventPublisher) {
        var scanner = new SpringModelScanner(context, registry, eventPublisher);
        scanner.scan();
        return scanner;
    }

    @Bean
    @ConditionalOnMissingBean
    SpringOperationDispatcher springOperationDispatcher(DomainModelRegistry registry,
                                                         ApplicationContext context) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new SpringOperationDispatcher(registry, context, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    CaseHubToolCallbackProvider caseHubToolCallbackProvider(DomainModelRegistry registry,
                                                             SpringOperationDispatcher dispatcher) {
        return new CaseHubToolCallbackProvider(registry, dispatcher);
    }

    @Bean
    @ConditionalOnBean(McpSyncServer.class)
    SpringMcpResourceRegistryBridge springMcpResourceRegistryBridge(
            McpSyncServer server,
            ApplicationEventPublisher eventPublisher) {
        return new SpringMcpResourceRegistryBridge(server, eventPublisher);
    }

    @Bean
    @ConditionalOnBean(McpSyncServer.class)
    SpringDomainResourceRegistrar springDomainResourceRegistrar(
            McpResourceRegistry resourceRegistry,
            DomainModelRegistry domainModelRegistry) {
        return new SpringDomainResourceRegistrar(resourceRegistry, domainModelRegistry);
    }
}
