package io.casehub.platform.agent.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.ToolUnion;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.time.Duration;
import java.util.Objects;

public class AnthropicNativeChatModel implements ChatModel {

    private final AnthropicClient client;
    private final String defaultModel;
    private final Duration defaultTimeout;
    private final int maxTokens;

    public AnthropicNativeChatModel(AnthropicClient client, String defaultModel,
                                     Duration defaultTimeout, int maxTokens) {
        this.client = Objects.requireNonNull(client, "client");
        this.defaultModel = Objects.requireNonNull(defaultModel, "defaultModel");
        this.defaultTimeout = defaultTimeout != null ? defaultTimeout : Duration.ofSeconds(120);
        this.maxTokens = maxTokens > 0 ? maxTokens : 8192;
    }

    public AnthropicNativeChatModel(AnthropicClient client, String defaultModel,
                                     Duration defaultTimeout) {
        this(client, defaultModel, defaultTimeout, 8192);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String modelName = request.parameters().modelName() != null
                ? request.parameters().modelName()
                : defaultModel;

        var systemBlocks = LangChain4jAnthropicMapper.extractSystemBlocks(request.messages());
        var messages = LangChain4jAnthropicMapper.toAnthropicMessages(request.messages());

        int effectiveMaxTokens = request.parameters().maxOutputTokens() != null
                ? request.parameters().maxOutputTokens()
                : maxTokens;

        var paramsBuilder = MessageCreateParams.builder()
                .model(modelName)
                .messages(messages)
                .maxTokens(effectiveMaxTokens);

        if (!systemBlocks.isEmpty()) {
            paramsBuilder.systemOfTextBlockParams(systemBlocks);
        }

        var tools = request.parameters().toolSpecifications();
        if (tools != null && !tools.isEmpty()) {
            var anthropicTools = LangChain4jAnthropicMapper.toAnthropicTools(tools);
            paramsBuilder.tools(anthropicTools.stream()
                    .map(ToolUnion::ofTool)
                    .toList());
        }

        Message response = client.messages().create(paramsBuilder.build());
        return LangChain4jAnthropicMapper.toChatResponse(response);
    }

    public String defaultModel() { return defaultModel; }
}
