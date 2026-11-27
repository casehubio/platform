package io.casehub.platform.agent.service.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentResolver;
import io.casehub.platform.agent.service.AgentServiceBeans;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(AgentServiceBeans.class)
@AutoConfigureAfter(name = "io.casehub.platform.agent.gate.spring.AgentGateSpringAutoConfiguration")
public class AgentServiceAutoConfiguration {

    @Bean
    AgentServiceBeans agentServiceBeans(AgentProvider agentProvider, AgentResolver agentResolver,
                                        ObjectProvider<ObjectMapper> objectMapper) {
        return new AgentServiceBeans(agentProvider, agentResolver, objectMapper.getIfAvailable());
    }

    @Bean
    static AgentServiceBeanRegistrar agentServiceBeanRegistrar() {
        return new AgentServiceBeanRegistrar();
    }
}
