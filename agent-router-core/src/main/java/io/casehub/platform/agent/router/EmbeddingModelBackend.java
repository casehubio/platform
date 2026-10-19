package io.casehub.platform.agent.router;

import dev.langchain4j.model.embedding.EmbeddingModel;

public interface EmbeddingModelBackend {

    String key();

    EmbeddingModel embeddingModel();
}
