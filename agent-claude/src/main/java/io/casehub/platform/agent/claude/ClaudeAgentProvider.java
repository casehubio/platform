package io.casehub.platform.agent.claude;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Claude Agent SDK implementation of {@link AgentProvider}.
 *
 * <p>Activates when {@code casehub-platform-agent-claude} is on the classpath —
 * {@code @ApplicationScoped} beats {@code NoOpAgentProvider @DefaultBean} automatically.
 *
 * <p>CDI tier for {@code AgentProvider}:
 * <ul>
 *   <li>Tier 1: {@code NoOpAgentProvider @DefaultBean} (platform/) — default
 *   <li>Tier 2: {@code ClaudeAgentProvider @ApplicationScoped} (agent-claude/) — active on classpath
 * </ul>
 *
 * <p>App code injects {@link AgentProvider}, not {@link ClaudeAgentClient} directly.
 */
@ApplicationScoped
public class ClaudeAgentProvider implements AgentProvider {

    @Inject
    ClaudeAgentClient client;

    @Override
    public Multi<AgentEvent> invoke(final AgentSessionConfig config) {
        return client.run(config);
    }

    @Override
    public AgentSession openSession(final AgentSessionInit init) {
        return client.openSession(init);
    }
}
