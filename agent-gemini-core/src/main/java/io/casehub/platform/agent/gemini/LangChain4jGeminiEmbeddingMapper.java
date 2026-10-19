package io.casehub.platform.agent.gemini;

import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.embedding.response.EmbeddingResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;

final class LangChain4jGeminiEmbeddingMapper {

    private LangChain4jGeminiEmbeddingMapper() {}

    static EmbeddingResponse toEmbeddingResponse(EmbedContentResponse response, String modelName) {
        List<Embedding> embeddings = response.embeddings()
                .orElse(List.of())
                .stream()
                .map(ce -> {
                    List<Float> values = ce.values().orElse(List.of());
                    float[] vector = new float[values.size()];
                    for (int i = 0; i < values.size(); i++) {
                        vector[i] = values.get(i);
                    }
                    return Embedding.from(vector);
                })
                .toList();

        TokenUsage tokenUsage = null;
        if (response.metadata().isPresent()) {
            int chars = response.metadata().get().billableCharacterCount().orElse(0);
            tokenUsage = new TokenUsage(chars, 0);
        }

        return EmbeddingResponse.builder()
                .embeddings(embeddings)
                .metadata(EmbeddingResponseMetadata.builder()
                        .modelName(modelName)
                        .tokenUsage(tokenUsage)
                        .build())
                .build();
    }

    static String toGeminiTaskType(EmbeddingInputType inputType) {
        if (inputType == null) return null;
        return switch (inputType) {
            case QUERY -> "RETRIEVAL_QUERY";
            case DOCUMENT -> "RETRIEVAL_DOCUMENT";
        };
    }
}
