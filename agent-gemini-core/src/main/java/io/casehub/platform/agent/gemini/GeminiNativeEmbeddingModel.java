package io.casehub.platform.agent.gemini;

import com.google.genai.Client;
import com.google.genai.types.EmbedContentConfig;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;

import java.util.Objects;

public class GeminiNativeEmbeddingModel implements EmbeddingModel {

    private final Client client;
    private final String defaultModel;
    private final Integer defaultDimensions;

    public GeminiNativeEmbeddingModel(Client client, String defaultModel, Integer defaultDimensions) {
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

        var configBuilder = EmbedContentConfig.builder();

        Integer dimensions = request.dimensions() != null ? request.dimensions() : defaultDimensions;
        if (dimensions != null) {
            configBuilder.outputDimensionality(dimensions);
        }

        String taskType = LangChain4jGeminiEmbeddingMapper.toGeminiTaskType(request.inputType());
        if (taskType != null) {
            configBuilder.taskType(taskType);
        }

        var response = client.models.embedContent(modelName, texts, configBuilder.build());
        return LangChain4jGeminiEmbeddingMapper.toEmbeddingResponse(response, modelName);
    }

    @Override
    public String modelName() { return defaultModel; }
}
