package io.casehub.platform.agent.gemini.spring;

import io.casehub.platform.agent.gemini.GeminiAgentProperties;
import io.casehub.platform.agent.gemini.GeminiNativeEmbeddingModel;
import io.casehub.platform.agent.router.EmbeddingModelBackend;
import io.casehub.platform.agent.router.SimpleEmbeddingModelBackend;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(GeminiNativeEmbeddingModel.class)
public class GeminiEmbeddingModelManualConfig {

    @Bean
    public EmbeddingModelBackend geminiEmbeddingModelBackend(GeminiAgentProperties properties) {
        var clientBuilder = com.google.genai.Client.builder();
        properties.apiKey().ifPresent(clientBuilder::apiKey);
        GeminiNativeEmbeddingModel embeddingModel = new GeminiNativeEmbeddingModel(
                clientBuilder.build(), "text-embedding-004", null);
        return new SimpleEmbeddingModelBackend("gemini", embeddingModel);
    }
}
