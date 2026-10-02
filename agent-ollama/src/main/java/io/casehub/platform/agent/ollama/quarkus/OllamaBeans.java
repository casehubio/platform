package io.casehub.platform.agent.ollama.quarkus;

import io.casehub.platform.agent.ollama.OllamaAgentBackend;
import io.casehub.platform.agent.ollama.config.OllamaAgentConfig;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class OllamaBeans {

    private OllamaAgentBackend backend;

    @Produces
    @ApplicationScoped
    public OllamaAgentBackend ollamaAgentBackend(OllamaAgentConfig config) {
        backend = new OllamaAgentBackend(config);
        return backend;
    }

    @PreDestroy
    void shutdown() {
        if (backend != null) {
            backend.shutdown();
        }
    }
}
