package io.casehub.platform.agent.gemini;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.time.Duration;
import java.util.Objects;

public class GeminiNativeChatModel implements ChatModel {

    private final Client client;
    private final String defaultModel;
    private final Duration defaultTimeout;

    public GeminiNativeChatModel(Client client, String defaultModel, Duration defaultTimeout) {
        this.client = Objects.requireNonNull(client, "client");
        this.defaultModel = Objects.requireNonNull(defaultModel, "defaultModel");
        this.defaultTimeout = defaultTimeout != null ? defaultTimeout : Duration.ofSeconds(300);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String modelName = request.parameters().modelName() != null
                ? request.parameters().modelName()
                : defaultModel;

        Content systemInstruction = LangChain4jGeminiMapper.extractSystemInstruction(request.messages());
        var contents = LangChain4jGeminiMapper.toGeminiContents(request.messages());

        var configBuilder = GenerateContentConfig.builder();

        if (systemInstruction != null) {
            configBuilder.systemInstruction(systemInstruction);
        }

        var tools = request.parameters().toolSpecifications();
        if (tools != null && !tools.isEmpty()) {
            configBuilder.tools(LangChain4jGeminiMapper.toGeminiTools(tools));
        }

        if (request.parameters().maxOutputTokens() != null) {
            configBuilder.maxOutputTokens(request.parameters().maxOutputTokens());
        }

        if (request.parameters().responseFormat() != null) {
            var rf = request.parameters().responseFormat();
            if (rf.type() == dev.langchain4j.model.chat.request.ResponseFormatType.JSON) {
                configBuilder.responseMimeType("application/json");
                if (rf.jsonSchema() != null) {
                    configBuilder.responseJsonSchema(rf.jsonSchema().rootElement().toString());
                }
            }
        }

        GenerateContentResponse response = client.models.generateContent(
                modelName, contents, configBuilder.build());

        return LangChain4jGeminiMapper.toChatResponse(response, modelName);
    }

    public String defaultModel() { return defaultModel; }
}
