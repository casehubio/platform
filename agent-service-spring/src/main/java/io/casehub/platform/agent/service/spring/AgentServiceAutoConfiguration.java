package io.casehub.platform.agent.service.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.agent.AgentDescriptor;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentResolver;
import io.casehub.platform.agent.ResolvedAgent;
import io.casehub.platform.agent.service.AgentServiceBeans;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Optional;

@AutoConfiguration
@ConditionalOnClass(AgentServiceBeans.class)
@AutoConfigureAfter(name = {
        "io.casehub.platform.agent.router.spring.AgentRouterAutoConfiguration",
        "io.casehub.platform.agent.gate.spring.AgentGateSpringAutoConfiguration"})
public class AgentServiceAutoConfiguration {

    /**
     * Proxies need an {@link AgentProvider}; without one (no agent backend configured) the bean is skipped,
     * mirroring {@code AgentHttpAutoConfiguration}. {@link AgentResolver} is optional: when absent, agent ids
     * resolve to nothing.
     */
    @Bean
    @ConditionalOnBean(AgentProvider.class)
    @ConditionalOnMissingBean
    AgentServiceBeans agentServiceBeans(AgentProvider agentProvider,
                                        ObjectProvider<AgentResolver> agentResolver,
                                        ObjectProvider<ObjectMapper> objectMapper) {
        return new AgentServiceBeans(agentProvider,
                agentResolver.getIfAvailable(EmptyAgentResolver::new),
                objectMapper.getIfAvailable());
    }

    @Bean
    static AgentServiceBeanRegistrar agentServiceBeanRegistrar() {
        return new AgentServiceBeanRegistrar();
    }

    private static final class EmptyAgentResolver implements AgentResolver {
        @Override
        public Optional<ResolvedAgent> resolve(String agentId) {
            return Optional.empty();
        }

        @Override
        public List<AgentDescriptor> discover() {
            return List.of();
        }
    }
}
