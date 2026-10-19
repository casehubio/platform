package io.casehub.platform.agent.gemini.spring;

import io.casehub.platform.agent.gemini.GeminiAgentProperties;
import io.casehub.platform.agent.gemini.GeminiNativeChatModel;
import io.casehub.platform.agent.router.ChatModelBackend;
import io.casehub.platform.agent.router.SimpleChatModelBackend;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(GeminiNativeChatModel.class)
public class GeminiChatModelManualConfig {

    @Bean
    public ChatModelBackend geminiChatModelBackend(GeminiAgentProperties properties) {
        var clientBuilder = com.google.genai.Client.builder();
        properties.apiKey().ifPresent(clientBuilder::apiKey);
        GeminiNativeChatModel chatModel = new GeminiNativeChatModel(
                clientBuilder.build(), properties.defaultModel(), properties.defaultTimeout());
        return new SimpleChatModelBackend("gemini", chatModel);
    }
}
