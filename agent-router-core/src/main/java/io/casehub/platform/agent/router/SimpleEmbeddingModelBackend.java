package io.casehub.platform.agent.router;

import dev.langchain4j.model.embedding.EmbeddingModel;

public record SimpleEmbeddingModelBackend(String key, EmbeddingModel embeddingModel)
    implements EmbeddingModelBackend {}
