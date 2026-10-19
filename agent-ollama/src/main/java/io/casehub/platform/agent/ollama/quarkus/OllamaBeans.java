package io.casehub.platform.agent.ollama.quarkus;

import io.casehub.platform.agent.ollama.OllamaAgentBackend;
import io.casehub.platform.agent.ollama.config.OllamaAgentConfig;
import io.casehub.platform.agent.openai.OpenAiNativeChatModel;
import io.casehub.platform.agent.router.ChatModelBackend;
import io.casehub.platform.agent.router.SimpleChatModelBackend;
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


    @PreDestroy
    void shutdown() {
        if (backend != null) {
            backend.shutdown();
        }
    }
}
