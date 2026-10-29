package io.casehub.platform.agent.http.spring;

import io.casehub.platform.agent.AgentDescriptor;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentResolver;
import io.casehub.platform.agent.ResolvedAgent;
import io.casehub.platform.agent.SessionRegistry;
import io.casehub.platform.agent.http.AgentHttpService;
import io.casehub.platform.agent.http.HttpSessionManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Optional;

@AutoConfiguration
public class AgentHttpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AgentResolver noOpAgentResolver() {
        return new AgentResolver() {
            @Override
            public Optional<ResolvedAgent> resolve(String agentId) {
                return Optional.empty();
            }

            @Override
            public List<AgentDescriptor> discover() {
                return List.of();
            }
        };
    }

    @Bean
    @ConditionalOnBean(AgentProvider.class)
    @ConditionalOnMissingBean
    AgentHttpService agentHttpService(
            AgentProvider agentProvider,
            AgentResolver agentResolver,
            ObjectProvider<SessionRegistry> sessionRegistry) {
        SessionRegistry registry = sessionRegistry.getIfAvailable();
        HttpSessionManager sessionManager = registry != null
                ? new HttpSessionManager(registry, agentProvider)
                : null;
        return new AgentHttpService(agentProvider, agentResolver, sessionManager);
    }
}
