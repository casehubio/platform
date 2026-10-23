package io.casehub.platform.agent.geminicli.quarkus;

import io.casehub.platform.agent.AgentConcurrencyGate;
import io.casehub.platform.agent.AgentRuntime;
import io.casehub.platform.agent.geminicli.GeminiCliAgentBackend;
import io.casehub.platform.agent.geminicli.GeminiCliAgentProperties;
import io.casehub.platform.agent.geminicli.config.GeminiCliAgentConfig;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Duration;

@ApplicationScoped
public class GeminiCliBeans {

    private GeminiCliAgentBackend backend;

    @Produces
    @ApplicationScoped
    public GeminiCliAgentBackend geminiCliAgentBackend(GeminiCliAgentConfig config, AgentRuntime runtime,
                                                       AgentConcurrencyGate gate) {
        GeminiCliAgentProperties props = gate.isActive() ? new GeminiCliAgentProperties() {
            public String binaryPath() { return config.binaryPath(); }
            public Duration defaultTimeout() { return config.defaultTimeout(); }
            public int maxConcurrentSessions() { return 0; }
        } : config;
        backend = new GeminiCliAgentBackend(props, runtime);
        return backend;
    }

    @PreDestroy
    void shutdown() {
        if (backend != null) {
            backend.shutdown();
        }
    }
}
