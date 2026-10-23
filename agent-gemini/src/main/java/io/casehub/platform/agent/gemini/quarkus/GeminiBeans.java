package io.casehub.platform.agent.gemini.quarkus;

import io.casehub.platform.agent.AgentConcurrencyGate;
import io.casehub.platform.agent.gemini.GeminiAgentBackend;
import io.casehub.platform.agent.gemini.GeminiAgentProperties;
import io.casehub.platform.agent.gemini.GeminiNativeChatModel;
import io.casehub.platform.agent.gemini.config.GeminiAgentConfig;
import io.casehub.platform.agent.router.ChatModelBackend;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Duration;
import java.util.Optional;

@ApplicationScoped
public class GeminiBeans {

    private GeminiAgentBackend backend;

    @Produces
    @ApplicationScoped
    public GeminiAgentBackend geminiAgentBackend(GeminiAgentConfig config, AgentConcurrencyGate gate) {
        GeminiAgentProperties props = gate.isActive() ? new GeminiAgentProperties() {
            public Optional<String> apiKey() { return config.apiKey(); }
            public String defaultModel() { return config.defaultModel(); }
            public Duration cacheTtl() { return config.cacheTtl(); }
            public Duration defaultTimeout() { return config.defaultTimeout(); }
            public int maxConcurrentSessions() { return 0; }
        } : config;
        backend = new GeminiAgentBackend(props);
        return backend;
    }

    @Produces
    @ApplicationScoped
    public ChatModelBackend geminiChatModelBackend(GeminiAgentConfig config) {
        var clientBuilder = com.google.genai.Client.builder();
        config.apiKey().ifPresent(clientBuilder::apiKey);
        GeminiNativeChatModel chatModel = new GeminiNativeChatModel(
                clientBuilder.build(), config.defaultModel(), config.defaultTimeout());
        return new io.casehub.platform.agent.router.SimpleChatModelBackend("gemini", chatModel);
    }

    @Produces
    @ApplicationScoped
    public io.casehub.platform.agent.router.EmbeddingModelBackend geminiEmbeddingModelBackend(GeminiAgentConfig config) {
        var clientBuilder = com.google.genai.Client.builder();
        config.apiKey().ifPresent(clientBuilder::apiKey);
        io.casehub.platform.agent.gemini.GeminiNativeEmbeddingModel embeddingModel =
                new io.casehub.platform.agent.gemini.GeminiNativeEmbeddingModel(
                        clientBuilder.build(), "text-embedding-004", null);
        return new io.casehub.platform.agent.router.SimpleEmbeddingModelBackend("gemini", embeddingModel);
    }


    @PreDestroy
    void shutdown() {
        if (backend != null) {
            backend.shutdown();
        }
    }
}
