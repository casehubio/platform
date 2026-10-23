package io.casehub.platform.agent.ollama.quarkus;

import io.casehub.platform.agent.AgentConcurrencyGate;
import io.casehub.platform.agent.ollama.OllamaAgentBackend;
import io.casehub.platform.agent.ollama.OllamaAgentProperties;
import io.casehub.platform.agent.ollama.config.OllamaAgentConfig;
import io.casehub.platform.agent.openai.OpenAiNativeChatModel;
import io.casehub.platform.agent.router.ChatModelBackend;
import io.casehub.platform.agent.router.SimpleChatModelBackend;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Duration;

@ApplicationScoped
public class OllamaBeans {

    private OllamaAgentBackend backend;

    @Produces
    @ApplicationScoped
    public OllamaAgentBackend ollamaAgentBackend(OllamaAgentConfig config, AgentConcurrencyGate gate) {
        OllamaAgentProperties props = gate.isActive() ? new OllamaAgentProperties() {
            public String host() { return config.host(); }
            public String defaultModel() { return config.defaultModel(); }
            public Duration defaultTimeout() { return config.defaultTimeout(); }
            public int maxConcurrentSessions() { return 0; }
        } : config;
        backend = new OllamaAgentBackend(props);
        return backend;
    }

    @Produces
    @ApplicationScoped
    public ChatModelBackend ollamaChatModelBackend(OllamaAgentConfig config) {
        var client = com.openai.client.okhttp.OpenAIOkHttpClient.builder()
                                                                .baseUrl(config.host() + "/v1/")
                                                                .apiKey("ollama")
                                                                .build();
        OpenAiNativeChatModel chatModel = new OpenAiNativeChatModel(
                client, config.defaultModel(), config.defaultTimeout());
        return new SimpleChatModelBackend("ollama", chatModel);
    }

    @Produces
    @ApplicationScoped
    public io.casehub.platform.agent.router.EmbeddingModelBackend ollamaEmbeddingModelBackend(OllamaAgentConfig config) {
        var client = com.openai.client.okhttp.OpenAIOkHttpClient.builder()
                                                                .baseUrl(config.host() + "/v1/")
                                                                .apiKey("ollama")
                                                                .build();
        io.casehub.platform.agent.openai.OpenAiNativeEmbeddingModel embeddingModel =
                new io.casehub.platform.agent.openai.OpenAiNativeEmbeddingModel(
                        client, config.defaultModel(), null);
        return new io.casehub.platform.agent.router.SimpleEmbeddingModelBackend("ollama", embeddingModel);
    }


    @PreDestroy
    void shutdown() {
        if (backend != null) {
            backend.shutdown();
        }
    }
}
