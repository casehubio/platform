package io.casehub.platform.agent.ollama.spring;

import io.casehub.platform.agent.ollama.OllamaAgentProperties;
import io.casehub.platform.agent.openai.OpenAiNativeChatModel;
import io.casehub.platform.agent.router.ChatModelBackend;
import io.casehub.platform.agent.router.SimpleChatModelBackend;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(OpenAiNativeChatModel.class)
public class OllamaChatModelManualConfig {

    @Bean
    public ChatModelBackend ollamaChatModelBackend(OllamaAgentProperties properties) {
        var client = com.openai.client.okhttp.OpenAIOkHttpClient.builder()
                .baseUrl(properties.host() + "/v1/")
                .apiKey("ollama")
                .build();
        OpenAiNativeChatModel chatModel = new OpenAiNativeChatModel(
                client, properties.defaultModel(), properties.defaultTimeout());
        return new SimpleChatModelBackend("ollama", chatModel);
    }
}
