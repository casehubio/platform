package io.casehub.platform.agent.openai.quarkus;

import io.casehub.platform.agent.AgentConcurrencyGate;
import io.casehub.platform.agent.openai.OpenAiAgentBackend;
import io.casehub.platform.agent.openai.OpenAiAgentProperties;
import io.casehub.platform.agent.openai.OpenAiNativeChatModel;
import io.casehub.platform.agent.openai.OpenAiNativeEmbeddingModel;
import io.casehub.platform.agent.openai.config.OpenAiAgentConfig;
import io.casehub.platform.agent.router.ChatModelBackend;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Duration;
import java.util.Optional;

@ApplicationScoped
public class OpenAiBeans {

    private OpenAiAgentBackend backend;

    @Produces
    @ApplicationScoped
    public OpenAiAgentBackend openAiAgentBackend(OpenAiAgentConfig config, AgentConcurrencyGate gate) {
        OpenAiAgentProperties props = gate.isActive() ? new OpenAiAgentProperties() {
            public Optional<String> apiKey() { return config.apiKey(); }
            public String defaultModel() { return config.defaultModel(); }
            public String promptCacheRetention() { return config.promptCacheRetention(); }
            public Duration defaultTimeout() { return config.defaultTimeout(); }
            public int maxConcurrentSessions() { return 0; }
        } : config;
        backend = new OpenAiAgentBackend(props);
        return backend;
    }

    @Produces
    @ApplicationScoped
    public ChatModelBackend openAiChatModelBackend(OpenAiAgentConfig config) {
        var clientBuilder = com.openai.client.okhttp.OpenAIOkHttpClient.builder();
        config.apiKey().ifPresent(clientBuilder::apiKey);
        OpenAiNativeChatModel chatModel = new OpenAiNativeChatModel(
                clientBuilder.build(), config.defaultModel(), config.defaultTimeout());
        return new io.casehub.platform.agent.router.SimpleChatModelBackend("openai", chatModel);
    }

    @Produces
    @ApplicationScoped
    public io.casehub.platform.agent.router.EmbeddingModelBackend openAiEmbeddingModelBackend(OpenAiAgentConfig config) {
        var clientBuilder = com.openai.client.okhttp.OpenAIOkHttpClient.builder();
        config.apiKey().ifPresent(clientBuilder::apiKey);
        OpenAiNativeEmbeddingModel embeddingModel = new OpenAiNativeEmbeddingModel(
                clientBuilder.build(), "text-embedding-3-small", null);
        return new io.casehub.platform.agent.router.SimpleEmbeddingModelBackend("openai", embeddingModel);
    }


    @PreDestroy
    void shutdown() {
        if (backend != null) {
            backend.shutdown();
        }
    }
}
