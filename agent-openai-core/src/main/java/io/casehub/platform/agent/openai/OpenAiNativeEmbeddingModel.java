package io.casehub.platform.agent.openai;

import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.EmbeddingCreateParams;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;

import java.util.Objects;

public class OpenAiNativeEmbeddingModel implements EmbeddingModel {

    private final OpenAIClient client;
    private final String defaultModel;
    private final Integer defaultDimensions;

    public OpenAiNativeEmbeddingModel(OpenAIClient client, String defaultModel, Integer defaultDimensions) {
        this.client = Objects.requireNonNull(client, "client");
        this.defaultModel = Objects.requireNonNull(defaultModel, "defaultModel");
        this.defaultDimensions = defaultDimensions;
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {
        String modelName = request.modelName() != null ? request.modelName() : defaultModel;

        var texts = request.inputs().stream()
                .map(input -> input.text())
                .toList();

        var paramsBuilder = EmbeddingCreateParams.builder()
                .model(com.openai.models.embeddings.EmbeddingModel.of(modelName))
                .inputOfArrayOfStrings(texts);

        Integer dimensions = request.dimensions() != null ? request.dimensions() : defaultDimensions;
        if (dimensions != null) {
            paramsBuilder.dimensions(dimensions.longValue());
        }

        var response = client.embeddings().create(paramsBuilder.build());
        return LangChain4jOpenAiEmbeddingMapper.toEmbeddingResponse(response);
    }

    @Override
    public String modelName() { return defaultModel; }
}
