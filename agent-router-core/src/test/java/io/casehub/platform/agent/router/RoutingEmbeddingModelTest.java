package io.casehub.platform.agent.router;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.embedding.response.EmbeddingResponseMetadata;
import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutingEmbeddingModelTest {

    @Test
    void routesToBackendViaModelRegistry() {
        var openAiModel = stubEmbeddingModel("openai-result");
        var geminiModel = stubEmbeddingModel("gemini-result");
        var backends = List.<EmbeddingModelBackend>of(
                new SimpleEmbeddingModelBackend("openai", openAiModel),
                new SimpleEmbeddingModelBackend("gemini", geminiModel));

        var registry = mock(ModelRegistry.class);
        var descriptor = mock(ModelDescriptor.class);
        when(descriptor.backendKey()).thenReturn("gemini");
        when(registry.resolveById("text-embedding-004")).thenReturn(Optional.of(descriptor));

        var router = new RoutingEmbeddingModel(backends, registry, "openai");

        var request = EmbeddingRequest.builder().input("test").modelName("text-embedding-004").build();
        var response = router.doEmbed(request);

        assertThat(response.metadata().modelName()).isEqualTo("gemini-result");
    }

    @Test
    void fallsBackToDefaultWhenModelNotInRegistry() {
        var openAiModel = stubEmbeddingModel("openai-result");
        var backends = List.<EmbeddingModelBackend>of(
                new SimpleEmbeddingModelBackend("openai", openAiModel));

        var registry = mock(ModelRegistry.class);
        when(registry.resolveById("unknown-model")).thenReturn(Optional.empty());

        var router = new RoutingEmbeddingModel(backends, registry, "openai");

        var request = EmbeddingRequest.builder().input("test").modelName("unknown-model").build();
        var response = router.doEmbed(request);

        assertThat(response.metadata().modelName()).isEqualTo("openai-result");
    }

    @Test
    void throwsWhenNoBackendAvailable() {
        var registry = mock(ModelRegistry.class);
        var router = new RoutingEmbeddingModel(List.of(), registry, "openai");

        var request = EmbeddingRequest.builder().input("test").build();
        assertThatThrownBy(() -> router.doEmbed(request))
                .isInstanceOf(IllegalStateException.class);
    }

    private static EmbeddingModel stubEmbeddingModel(String modelName) {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse doEmbed(EmbeddingRequest request) {
                return EmbeddingResponse.builder()
                        .embeddings(List.of(Embedding.from(new float[]{1.0f, 2.0f, 3.0f})))
                        .metadata(EmbeddingResponseMetadata.builder().modelName(modelName).build())
                        .build();
            }
        };
    }
}
