package io.casehub.platform.agent.gate;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;

@Decorator
@Priority(Interceptor.Priority.APPLICATION)
public class GatedAgentProvider implements AgentProvider {

    @Inject @Delegate @Any AgentProvider delegate;
    @Inject AgentGateQuarkusProperties properties;
    @Inject SessionRegistry registry;

    private GatedAgentProviderWrapper wrapper;

    protected GatedAgentProvider() {}

    @PostConstruct
    void init() {
        wrapper = new GatedAgentProviderWrapper(delegate, properties, registry);
    }

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        return wrapper.invoke(config);
    }

    @Override
    public AgentSession openSession(AgentSessionInit init) {
        return wrapper.openSession(init);
    }
}
