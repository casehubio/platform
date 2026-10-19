package io.casehub.platform.agent.claude.spring;

import io.casehub.platform.agent.claude.AnthropicNativeChatModel;
import io.casehub.platform.agent.router.ChatModelBackend;
import io.casehub.platform.agent.router.SimpleChatModelBackend;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(AnthropicNativeChatModel.class)
@EnableConfigurationProperties(AnthropicChatModelSpringProperties.class)
public class ClaudeChatModelManualConfig {

    @Bean
    public ChatModelBackend anthropicChatModelBackend(AnthropicChatModelSpringProperties config) {
        var clientBuilder = com.anthropic.client.okhttp.AnthropicOkHttpClient.builder();
        config.apiKey().ifPresent(clientBuilder::apiKey);
        AnthropicNativeChatModel chatModel = new AnthropicNativeChatModel(
                clientBuilder.build(), config.defaultModel(), config.defaultTimeout(), config.maxTokens());
        return new SimpleChatModelBackend("claude", chatModel);
    }
}
