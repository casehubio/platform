package io.casehub.platform.agent.claude.quarkus;

import dev.langchain4j.model.chat.ChatModel;
import io.casehub.platform.agent.claude.AnthropicNativeChatModel;
import io.casehub.platform.agent.claude.ClaudeAgentClient;
import io.casehub.platform.agent.claude.ClaudeAgentProvider;
import io.casehub.platform.agent.claude.config.AnthropicChatModelConfig;
import io.casehub.platform.agent.claude.config.ClaudeAgentConfig;
import io.casehub.platform.agent.router.ChatModelBackend;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@Startup
@ApplicationScoped
public class ClaudeBeans {

    private ClaudeAgentClient client;

    @Produces
    @ApplicationScoped
    public ClaudeAgentClient claudeAgentClient(ClaudeAgentConfig config) {
        client = new ClaudeAgentClient(config);
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
        return new ChatModelBackend() {
            @Override
            public String key()                                     {return "claude";}

            @Override
            public ChatModel chatModel() {return chatModel;}
        };
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
