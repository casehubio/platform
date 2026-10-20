package io.casehub.platform.agent.openai.spring;

import io.casehub.platform.agent.openai.OpenAiAgentProperties;
import io.casehub.platform.agent.openai.OpenAiNativeChatModel;
import io.casehub.platform.agent.router.ChatModelBackend;
import io.casehub.platform.agent.router.SimpleChatModelBackend;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(OpenAiNativeChatModel.class)
@ConditionalOnBean(OpenAiAgentProperties.class)
public class OpenAiChatModelManualConfig {

    @Bean
    public ChatModelBackend openAiChatModelBackend(OpenAiAgentProperties properties) {
        var clientBuilder = com.openai.client.okhttp.OpenAIOkHttpClient.builder();
        properties.apiKey().ifPresent(clientBuilder::apiKey);
        OpenAiNativeChatModel chatModel = new OpenAiNativeChatModel(
                clientBuilder.build(), properties.defaultModel(), properties.defaultTimeout());
        return new SimpleChatModelBackend("openai", chatModel);
    }
}
