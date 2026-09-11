package io.casehub.platform.agent.gate;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class AgentGateBeans {

    @Produces
    @ApplicationScoped
    public SessionRegistry sessionRegistry() {
        return new SessionRegistry();
    }
}
