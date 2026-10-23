package io.casehub.platform.agent;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpAgentConcurrencyGate implements AgentConcurrencyGate {

    @Override
    public boolean isActive() {
        return false;
    }
}
