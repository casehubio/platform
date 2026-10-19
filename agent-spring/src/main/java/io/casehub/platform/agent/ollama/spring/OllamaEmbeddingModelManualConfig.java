package io.casehub.platform.agent.ollama.spring;

import io.casehub.platform.agent.ollama.OllamaAgentProperties;
import io.casehub.platform.agent.openai.OpenAiNativeEmbeddingModel;
import io.casehub.platform.agent.router.EmbeddingModelBackend;
import io.casehub.platform.agent.router.SimpleEmbeddingModelBackend;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(OpenAiNativeEmbeddingModel.class)
public class OllamaEmbeddingModelManualConfig {

    @Bean
    public EmbeddingModelBackend ollamaEmbeddingModelBackend(OllamaAgentProperties properties) {
        var client = com.openai.client.okhttp.OpenAIOkHttpClient.builder()
                .baseUrl(properties.host() + "/v1/")
                .apiKey("ollama")
                .build();
        OpenAiNativeEmbeddingModel embeddingModel = new OpenAiNativeEmbeddingModel(
                client, properties.defaultModel(), null);
        return new SimpleEmbeddingModelBackend("ollama", embeddingModel);
    }
}
