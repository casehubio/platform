package io.casehub.platform.agent.claude.quarkus;

import io.casehub.platform.agent.AgentConcurrencyGate;
import io.casehub.platform.agent.claude.AnthropicNativeChatModel;
import io.casehub.platform.agent.claude.ClaudeAgentClient;
import io.casehub.platform.agent.claude.ClaudeAgentProperties;
import io.casehub.platform.agent.claude.ClaudeAgentProvider;
import io.casehub.platform.agent.claude.config.AnthropicChatModelConfig;
import io.casehub.platform.agent.claude.config.ClaudeAgentConfig;
import io.casehub.platform.agent.router.ChatModelBackend;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Duration;
import java.util.Optional;

@Startup
@ApplicationScoped
public class ClaudeBeans {

    private ClaudeAgentClient client;

    @Produces
    @ApplicationScoped
    public ClaudeAgentClient claudeAgentClient(ClaudeAgentConfig config, AgentConcurrencyGate gate) {
        ClaudeAgentProperties props = gate.isActive() ? new ClaudeAgentProperties() {
            public Optional<String> binaryPath() { return config.binaryPath(); }
            public Duration defaultTimeout() { return config.defaultTimeout(); }
            public int maxConcurrentSessions() { return 0; }
        } : config;
        client = new ClaudeAgentClient(props);
        return client;
    }

    @Produces
    @ApplicationScoped
    public ClaudeAgentProvider claudeAgentProvider(ClaudeAgentClient client) {
        return new ClaudeAgentProvider(client);
    }

    @Produces
    @ApplicationScoped
    public ChatModelBackend anthropicChatModelBackend(AnthropicChatModelConfig config) {
        var clientBuilder = com.anthropic.client.okhttp.AnthropicOkHttpClient.builder();
        config.apiKey().ifPresent(clientBuilder::apiKey);
        AnthropicNativeChatModel chatModel = new AnthropicNativeChatModel(
                clientBuilder.build(), config.defaultModel(), config.defaultTimeout(), config.maxTokens());
        return new io.casehub.platform.agent.router.SimpleChatModelBackend("claude", chatModel);
    }


    @PostConstruct
    void validateBinary() {
        if (client != null) {
            client.validateBinary();
        }
    }

    @PreDestroy
    void shutdown() {
        if (client != null) {
            client.shutdown();
        }
    }
}
