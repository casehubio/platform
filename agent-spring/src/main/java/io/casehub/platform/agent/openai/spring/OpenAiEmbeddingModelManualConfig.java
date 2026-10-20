package io.casehub.platform.agent.openai.spring;

import io.casehub.platform.agent.openai.OpenAiAgentProperties;
import io.casehub.platform.agent.openai.OpenAiNativeEmbeddingModel;
import io.casehub.platform.agent.router.EmbeddingModelBackend;
import io.casehub.platform.agent.router.SimpleEmbeddingModelBackend;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(OpenAiNativeEmbeddingModel.class)
@ConditionalOnBean(OpenAiAgentProperties.class)
public class OpenAiEmbeddingModelManualConfig {

    @Bean
    public EmbeddingModelBackend openAiEmbeddingModelBackend(OpenAiAgentProperties properties) {
        var clientBuilder = com.openai.client.okhttp.OpenAIOkHttpClient.builder();
        properties.apiKey().ifPresent(clientBuilder::apiKey);
        OpenAiNativeEmbeddingModel embeddingModel = new OpenAiNativeEmbeddingModel(
                clientBuilder.build(), "text-embedding-3-small", null);
        return new SimpleEmbeddingModelBackend("openai", embeddingModel);
    }
}
