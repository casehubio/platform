package io.casehub.platform.agent.ollama.quarkus;

import io.casehub.platform.agent.AgentConcurrencyGate;
import io.casehub.platform.agent.config.AgentManifestResult;
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
import java.util.Map;

@ApplicationScoped
public class OllamaBeans {

    private OllamaAgentBackend backend;

    @Produces
    @ApplicationScoped
    public OllamaAgentBackend ollamaAgentBackend(
            OllamaAgentConfig config, AgentConcurrencyGate gate, AgentManifestResult manifest) {
        Map<String, String> mp = manifest.providers().getOrDefault("ollama", Map.of());
        OllamaAgentProperties props = new OllamaAgentProperties() {
            public String host() {
                return mp.getOrDefault("host", config.host());
            }
            public String defaultModel() {
                return mp.getOrDefault("default-model", config.defaultModel());
            }
            public Duration defaultTimeout() {
                String val = mp.get("default-timeout");
                return val != null ? Duration.parse(val) : config.defaultTimeout();
            }
            public int maxConcurrentSessions() {
                if (gate.isActive()) return 0;
                String val = mp.get("max-concurrent-sessions");
                return val != null ? Integer.parseInt(val) : config.maxConcurrentSessions();
            }
        };
        backend = new OllamaAgentBackend(props);
        return backend;
    }

    @Produces
    @ApplicationScoped
    public ChatModelBackend ollamaChatModelBackend(OllamaAgentConfig config, AgentManifestResult manifest) {
        Map<String, String> mp = manifest.providers().getOrDefault("ollama", Map.of());
        String host = mp.getOrDefault("host", config.host());
        String model = mp.getOrDefault("default-model", config.defaultModel());
        String timeoutVal = mp.get("default-timeout");
        Duration timeout = timeoutVal != null ? Duration.parse(timeoutVal) : config.defaultTimeout();

        var client = com.openai.client.okhttp.OpenAIOkHttpClient.builder()
                                                                .baseUrl(host + "/v1/")
                                                                .apiKey("ollama")
                                                                .build();
        OpenAiNativeChatModel chatModel = new OpenAiNativeChatModel(client, model, timeout);
        return new SimpleChatModelBackend("ollama", chatModel);
    }

    @Produces
    @ApplicationScoped
    public io.casehub.platform.agent.router.EmbeddingModelBackend ollamaEmbeddingModelBackend(
            OllamaAgentConfig config, AgentManifestResult manifest) {
        Map<String, String> mp = manifest.providers().getOrDefault("ollama", Map.of());
        String host = mp.getOrDefault("host", config.host());
        String model = mp.getOrDefault("default-model", config.defaultModel());

        var client = com.openai.client.okhttp.OpenAIOkHttpClient.builder()
                                                                .baseUrl(host + "/v1/")
                                                                .apiKey("ollama")
                                                                .build();
        io.casehub.platform.agent.openai.OpenAiNativeEmbeddingModel embeddingModel =
                new io.casehub.platform.agent.openai.OpenAiNativeEmbeddingModel(client, model, null);
        return new io.casehub.platform.agent.router.SimpleEmbeddingModelBackend("ollama", embeddingModel);
    }

    @PreDestroy
    void shutdown() {
        if (backend != null) {
            backend.shutdown();
        }
    }
}
