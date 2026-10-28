package io.casehub.platform.agent.rest.quarkus;

import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentResolver;
import io.casehub.platform.agent.rest.AgentChatService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

public class AgentRestBeans {

    @Produces
    @ApplicationScoped
    public AgentChatService agentChatService(AgentProvider agentProvider,
                                              AgentResolver agentResolver) {
        return new AgentChatService(agentProvider, agentResolver);
    }
}
