package io.casehub.platform.agent.http.quarkus;

import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentResolver;
import io.casehub.platform.agent.BackendInstanceRegistry;
import io.casehub.platform.agent.SessionRegistry;
import io.casehub.platform.agent.http.AgentHttpService;
import io.casehub.platform.agent.http.HttpSessionManager;
import io.casehub.platform.api.model.ModelRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

public class AgentHttpBeans {

    @Produces
    @ApplicationScoped
    public AgentHttpService agentHttpService(
            AgentProvider agentProvider,
            Instance<AgentResolver> agentResolver,
            Instance<SessionRegistry> sessionRegistry,
            Instance<BackendInstanceRegistry> backendRegistry,
            Instance<ModelRegistry> modelRegistry) {
        HttpSessionManager sessionManager = sessionRegistry.isResolvable()
                ? new HttpSessionManager(sessionRegistry.get(), agentProvider)
                : null;
        return new AgentHttpService(
                agentProvider,
                agentResolver.isResolvable() ? agentResolver.get() : null,
                sessionManager,
                backendRegistry.isResolvable() ? backendRegistry.get() : null,
                modelRegistry.isResolvable() ? modelRegistry.get() : null);
    }
}
