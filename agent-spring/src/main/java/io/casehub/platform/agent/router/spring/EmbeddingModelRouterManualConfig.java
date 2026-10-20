package io.casehub.platform.agent.router.spring;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.casehub.platform.agent.router.EmbeddingModelBackend;
import io.casehub.platform.agent.router.RoutingEmbeddingModel;
import io.casehub.platform.api.model.ModelRegistry;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(RoutingEmbeddingModel.class)
@ConditionalOnBean(ModelRegistry.class)
public class EmbeddingModelRouterManualConfig {

    @Bean
    @ConditionalOnMissingBean(EmbeddingModel.class)
    public EmbeddingModel routingEmbeddingModel(
            List<EmbeddingModelBackend> backends,
            ModelRegistry modelRegistry,
            @Value("${casehub.platform.agent.default-backend:openai}") String defaultBackend) {
        return new RoutingEmbeddingModel(backends, modelRegistry, defaultBackend);
    }
}
