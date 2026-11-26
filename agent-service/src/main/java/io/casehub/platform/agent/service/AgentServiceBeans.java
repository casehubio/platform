package io.casehub.platform.agent.service;

import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.lang.reflect.Constructor;

@ApplicationScoped
public class AgentServiceBeans {

    @Inject AgentProvider agentProvider;
    @Inject AgentResolver agentResolver;


    public AgentServiceBeans() {}

    public AgentServiceBeans(AgentProvider agentProvider, AgentResolver agentResolver) {
        this.agentProvider = agentProvider;
        this.agentResolver = agentResolver;
    }


    public <T> T createProxy(Class<T> serviceInterface) {
        String proxyClassName = serviceInterface.getName() + "Proxy";
        try {
            @SuppressWarnings("unchecked")
            Class<T> proxyClass = (Class<T>) serviceInterface.getClassLoader().loadClass(proxyClassName);
            Constructor<T> constructor = proxyClass.getConstructor(AgentProvider.class, AgentResolver.class);
            return constructor.newInstance(agentProvider, agentResolver);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Failed to create proxy for @AgentService interface " + serviceInterface.getName()
                + ". Ensure agent-service-generator is on the annotation processor path.", e);
        }
    }
}
