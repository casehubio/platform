package io.casehub.platform.agent.codex.quarkus;

import io.casehub.platform.agent.AgentConcurrencyGate;
import io.casehub.platform.agent.AgentRuntime;
import io.casehub.platform.agent.codex.CodexAgentBackend;
import io.casehub.platform.agent.codex.CodexAgentProperties;
import io.casehub.platform.agent.codex.config.CodexAgentConfig;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Duration;

@ApplicationScoped
public class CodexBeans {

    private CodexAgentBackend backend;

    @Produces
    @ApplicationScoped
    public CodexAgentBackend codexAgentBackend(CodexAgentConfig config, AgentRuntime runtime,
                                               AgentConcurrencyGate gate) {
        CodexAgentProperties props = gate.isActive() ? new CodexAgentProperties() {
            public String binaryPath() { return config.binaryPath(); }
            public Duration defaultTimeout() { return config.defaultTimeout(); }
            public int maxConcurrentSessions() { return 0; }
        } : config;
        backend = new CodexAgentBackend(props, runtime);
        return backend;
    }

    @PreDestroy
    void shutdown() {
        if (backend != null) {
            backend.shutdown();
        }
    }
}
