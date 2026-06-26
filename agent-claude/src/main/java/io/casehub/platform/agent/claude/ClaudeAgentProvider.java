package io.casehub.platform.agent.claude;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

/**
 * Claude Agent SDK implementation of {@link AgentProvider}.
 *
 * <p>Activates when {@code casehub-platform-agent-claude} is on the classpath —
 * {@code @Alternative @Priority(10)} beats both the no-op and LangChain4j adapters.
 *
 * <p>CDI tier for {@code AgentProvider}:
 * <ul>
 *   <li>Tier 0: {@code NoOpAgentProvider @DefaultBean} (platform/) — fallback
 *   <li>Tier 1: {@code ChatModelAgentProvider @Alternative @Priority(1)} (agent-langchain4j/) — any LangChain4j model
 *   <li>Tier 10: {@code ClaudeAgentProvider @Alternative @Priority(10)} (agent-claude/) — native Claude
 * </ul>
 *
 * <p>App code injects {@link AgentProvider}, not {@link ClaudeAgentClient} directly.
 */
@Alternative
@Priority(10)
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
