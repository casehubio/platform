package io.casehub.platform.agent.claude;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Claude Agent SDK implementation of {@link AgentBackend}.
 *
 * <p>Discovered by {@code RoutingAgentProvider} via {@code Instance<AgentBackend>}
 * with key {@code "claude"}.
 *
 * <p>App code injects {@link AgentProvider}, not this class directly.
 */
@ApplicationScoped
public class ClaudeAgentProvider implements AgentBackend {

    @Inject
    ClaudeAgentClient client;

    @Override
    public String key() {return "claude";}

    @Override
    public Multi<AgentEvent> invoke(final AgentSessionConfig config) {
        return client.run(config);
    }

    @Override
    public AgentSession openSession(final AgentSessionInit init) {
        return client.openSession(init);
    }
}
