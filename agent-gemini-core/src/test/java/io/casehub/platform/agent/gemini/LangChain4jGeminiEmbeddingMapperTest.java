package io.casehub.platform.agent.gemini;

import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentMetadata;
import com.google.genai.types.EmbedContentResponse;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LangChain4jGeminiEmbeddingMapperTest {

    @Test
    void mapsEmbeddingResponse() {
        var geminiResponse = EmbedContentResponse.builder()
                .embeddings(List.of(
                        ContentEmbedding.builder()
                                .values(List.of(0.1f, 0.2f, 0.3f))
                                .build()))
                .metadata(EmbedContentMetadata.builder()
                        .billableCharacterCount(25)
                        .build())
                .build();

        EmbeddingResponse response = LangChain4jGeminiEmbeddingMapper.toEmbeddingResponse(
                geminiResponse, "text-embedding-004");

        assertThat(response.embeddings()).hasSize(1);
        assertThat(response.embeddings().get(0).vector()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(response.metadata().modelName()).isEqualTo("text-embedding-004");
        assertThat(response.metadata().tokenUsage().inputTokenCount()).isEqualTo(25);
    }

    @Test
    void mapsBatchEmbeddingResponse() {
        var geminiResponse = EmbedContentResponse.builder()
                .embeddings(List.of(
                        ContentEmbedding.builder().values(List.of(0.1f, 0.2f)).build(),
                        ContentEmbedding.builder().values(List.of(0.3f, 0.4f)).build()))
                .build();

        EmbeddingResponse response = LangChain4jGeminiEmbeddingMapper.toEmbeddingResponse(
                geminiResponse, "text-embedding-004");
        assertThat(response.embeddings()).hasSize(2);
        assertThat(response.embeddings().get(0).vector()).containsExactly(0.1f, 0.2f);
        assertThat(response.embeddings().get(1).vector()).containsExactly(0.3f, 0.4f);
    }

    @Test
    void mapsTaskType() {
        assertThat(LangChain4jGeminiEmbeddingMapper.toGeminiTaskType(EmbeddingInputType.QUERY))
                .isEqualTo("RETRIEVAL_QUERY");
        assertThat(LangChain4jGeminiEmbeddingMapper.toGeminiTaskType(EmbeddingInputType.DOCUMENT))
                .isEqualTo("RETRIEVAL_DOCUMENT");
        assertThat(LangChain4jGeminiEmbeddingMapper.toGeminiTaskType(null)).isNull();
    }
}
