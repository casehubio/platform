package io.casehub.platform.agent.openai;

import com.openai.models.embeddings.CreateEmbeddingResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.embedding.response.EmbeddingResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;

final class LangChain4jOpenAiEmbeddingMapper {

    private LangChain4jOpenAiEmbeddingMapper() {}

    static EmbeddingResponse toEmbeddingResponse(CreateEmbeddingResponse response) {
        List<Embedding> embeddings = response.data().stream()
                .map(e -> {
                    List<Float> floats = e.embedding();
                    float[] vector = new float[floats.size()];
                    for (int i = 0; i < floats.size(); i++) {
                        vector[i] = floats.get(i);
                    }
                    return Embedding.from(vector);
                })
                .toList();

        TokenUsage tokenUsage = new TokenUsage(
                (int) response.usage().promptTokens(),
                0,
                (int) response.usage().totalTokens());

        return EmbeddingResponse.builder()
                .embeddings(embeddings)
                .metadata(EmbeddingResponseMetadata.builder()
                        .modelName(response.model())
                        .tokenUsage(tokenUsage)
                        .build())
                .build();
    }
}
