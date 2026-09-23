package io.casehub.platform.agent.gate.spring;

import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.gate.AgentGateProperties;
import io.casehub.platform.agent.gate.GatedAgentProviderWrapper;
import io.casehub.platform.agent.gate.SessionRegistry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

import java.util.logging.Logger;

class AgentGateBeanPostProcessor implements BeanPostProcessor, Ordered {

    private static final Logger LOG = Logger.getLogger(
            AgentGateBeanPostProcessor.class.getName());

    private final AgentGateProperties properties;
    private final SessionRegistry registry;

    AgentGateBeanPostProcessor(AgentGateProperties properties,
                                SessionRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    @Override
    public int getOrder() {
        return 2000;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
            throws BeansException {
        if (bean instanceof AgentProvider provider
                && !(bean instanceof GatedAgentProviderWrapper)) {
            LOG.info("Wrapping AgentProvider '" + beanName
                    + "' with agent gate rate limiter");
            return new GatedAgentProviderWrapper(provider, properties, registry);
        }
        return bean;
    }
}
