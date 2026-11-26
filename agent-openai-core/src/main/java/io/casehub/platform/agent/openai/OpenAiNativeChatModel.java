package io.casehub.platform.agent.openai;

import com.openai.client.OpenAIClient;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.time.Duration;
import java.util.Objects;

public class OpenAiNativeChatModel implements ChatModel {

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final OpenAIClient client;
    private final String       defaultModel;
    private final Duration     defaultTimeout;
    private final boolean      store;

    public OpenAiNativeChatModel(OpenAIClient client, String defaultModel,
                                 Duration defaultTimeout, boolean store) {
        this.client         = Objects.requireNonNull(client, "client");
        this.defaultModel   = Objects.requireNonNull(defaultModel, "defaultModel");
        this.defaultTimeout = defaultTimeout != null ? defaultTimeout : Duration.ofSeconds(120);
        this.store          = store;
    }

    public OpenAiNativeChatModel(OpenAIClient client, String defaultModel,
                                 Duration defaultTimeout) {
        this(client, defaultModel, defaultTimeout, false);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String modelName = request.parameters().modelName() != null
                           ? request.parameters().modelName()
                           : defaultModel;

        var paramsBuilder = ChatCompletionCreateParams.builder()
                                                      .model(com.openai.models.ChatModel.of(modelName))
                                                      .messages(LangChain4jOpenAiMapper.toOpenAiMessages(request.messages()));

        if (store) {
            paramsBuilder.store(true);
        }

        var tools = request.parameters().toolSpecifications();
        if (tools != null && !tools.isEmpty()) {
            paramsBuilder.tools(LangChain4jOpenAiMapper.toOpenAiTools(tools));
        }

        if (request.parameters().responseFormat() != null) {
            var rf = request.parameters().responseFormat();
            if (rf.type() == dev.langchain4j.model.chat.request.ResponseFormatType.JSON) {
                if (rf.jsonSchema() != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> schemaMap = OBJECT_MAPPER.readValue(
                                rf.jsonSchema().rootElement().toString(), java.util.Map.class);
                        paramsBuilder.responseFormat(ResponseFormatJsonSchema.builder()
                                                                             .jsonSchema(ResponseFormatJsonSchema.JsonSchema.builder()
                                                                                                                            .name(rf.jsonSchema().name())
                                                                                                                            .schema(com.openai.core.JsonValue.from(schemaMap))
                                                                                                                            .strict(true)
                                                                                                                            .build())
                                                                             .build());
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        throw new IllegalArgumentException("Invalid JSON schema: " + e.getMessage(), e);
                    }
                } else {
                    paramsBuilder.responseFormat(
                            com.openai.models.ResponseFormatJsonObject.builder().build());
                }
            }
        }

        ChatCompletion completion = client.chat().completions().create(paramsBuilder.build());
        return LangChain4jOpenAiMapper.toChatResponse(completion);
    }

    public String defaultModel() {return defaultModel;}
}
