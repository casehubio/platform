package io.casehub.platform.agent;

import io.quarkus.arc.DefaultBean;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * No-op {@link AgentProvider} active when no agent backend modules are on the classpath. Returns an empty completed stream.
 *
 * <p>A {@code WARN} log line is emitted on each invocation to make dev misconfiguration
 * immediately visible. The real agent and the NoOp both produce an empty completed Multi;
 * the log is the only observable distinction.
 */
@DefaultBean
@ApplicationScoped
public class NoOpAgentProvider implements AgentProvider {

    private static final Logger LOG = Logger.getLogger(NoOpAgentProvider.class);

    @Override
    public Multi<AgentEvent> invoke(final AgentSessionConfig config) {
        LOG.warn("NoOpAgentProvider is active — add casehub-platform-agent-router " +
                 "with at least one backend (agent-claude, agent-openai, agent-codex, " +
                 "agent-gemini, agent-gemini-cli, or agent-langchain4j) to the classpath");
        return Multi.createFrom().empty();
    }

    @Override
    public AgentSession openSession(final AgentSessionInit init) {
        LOG.warn("NoOpAgentProvider is active — add casehub-platform-agent-router " +
                 "with at least one backend (agent-claude, agent-openai, agent-codex, " +
                 "agent-gemini, agent-gemini-cli, or agent-langchain4j) to the classpath");
        return new NoOpAgentSession();
    }
}
