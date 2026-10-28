package io.casehub.platform.agent;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class NoOpAgentResolver implements AgentResolver {

    @Override
    public Optional<ResolvedAgent> resolve(String agentId) {
        return Optional.empty();
    }

    @Override
    public List<AgentDescriptor> discover() {
        return List.of();
    }
}
