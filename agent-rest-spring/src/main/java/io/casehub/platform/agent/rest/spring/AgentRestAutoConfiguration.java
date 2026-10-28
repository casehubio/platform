package io.casehub.platform.agent.rest.spring;

import io.casehub.platform.agent.*;
import io.casehub.platform.agent.rest.AgentChatService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Optional;

@AutoConfiguration
public class AgentRestAutoConfiguration {

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
    @ConditionalOnMissingBean
    AgentChatService agentChatService(AgentProvider agentProvider, AgentResolver agentResolver) {
        return new AgentChatService(agentProvider, agentResolver);
    }
}
