package io.casehub.platform.agent.gemini.quarkus;

import io.casehub.platform.agent.gemini.GeminiAgentBackend;
import io.casehub.platform.agent.gemini.GeminiNativeChatModel;
import io.casehub.platform.agent.gemini.config.GeminiAgentConfig;
import io.casehub.platform.agent.router.ChatModelBackend;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class GeminiBeans {

    private GeminiAgentBackend backend;

    @Produces
    @ApplicationScoped
    public GeminiAgentBackend geminiAgentBackend(GeminiAgentConfig config) {
        backend = new GeminiAgentBackend(config);
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
