package io.casehub.platform.agent.openai;

import com.openai.models.embeddings.CreateEmbeddingResponse;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LangChain4jOpenAiEmbeddingMapperTest {

    @Test
    void mapsEmbeddingResponse() {
        var openAiResponse = CreateEmbeddingResponse.builder()
                .data(List.of(
                        com.openai.models.embeddings.Embedding.builder()
                                .embedding(List.of(0.1f, 0.2f, 0.3f))
                                .index(0)
                                .build()))
                .model("text-embedding-3-small")
                .usage(CreateEmbeddingResponse.Usage.builder()
                        .promptTokens(5)
                        .totalTokens(5)
                        .build())
                .build();

        EmbeddingResponse response = LangChain4jOpenAiEmbeddingMapper.toEmbeddingResponse(openAiResponse);

        assertThat(response.embeddings()).hasSize(1);
        assertThat(response.embeddings().get(0).vector()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(response.metadata().modelName()).isEqualTo("text-embedding-3-small");
        assertThat(response.metadata().tokenUsage().inputTokenCount()).isEqualTo(5);
    }

    @Test
    void mapsBatchEmbeddingResponse() {
        var openAiResponse = CreateEmbeddingResponse.builder()
                .data(List.of(
                        com.openai.models.embeddings.Embedding.builder()
                                .embedding(List.of(0.1f, 0.2f))
                                .index(0).build(),
                        com.openai.models.embeddings.Embedding.builder()
                                .embedding(List.of(0.3f, 0.4f))
                                .index(1).build()))
                .model("text-embedding-3-small")
                .usage(CreateEmbeddingResponse.Usage.builder()
                        .promptTokens(10).totalTokens(10).build())
                .build();

        EmbeddingResponse response = LangChain4jOpenAiEmbeddingMapper.toEmbeddingResponse(openAiResponse);
        assertThat(response.embeddings()).hasSize(2);
        assertThat(response.embeddings().get(0).vector()).containsExactly(0.1f, 0.2f);
        assertThat(response.embeddings().get(1).vector()).containsExactly(0.3f, 0.4f);
    }
}
