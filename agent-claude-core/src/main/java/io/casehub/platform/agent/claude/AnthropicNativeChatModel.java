package io.casehub.platform.agent.claude;

import com.anthropic.client.AnthropicClient;
import io.casehub.platform.agent.CachePolicy;
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
    private final String          defaultModel;
    private final Duration        defaultTimeout;
    private final int             maxTokens;
    private final CachePolicy     cachePolicy;

    public AnthropicNativeChatModel(AnthropicClient client, String defaultModel,
                                    Duration defaultTimeout, int maxTokens,
                                    CachePolicy cachePolicy) {
        this.client         = Objects.requireNonNull(client, "client");
        this.defaultModel   = Objects.requireNonNull(defaultModel, "defaultModel");
        this.defaultTimeout = defaultTimeout != null ? defaultTimeout : Duration.ofSeconds(120);
        this.maxTokens      = maxTokens > 0 ? maxTokens : 8192;
        this.cachePolicy    = cachePolicy != null ? cachePolicy : CachePolicy.SYSTEM_AND_TOOLS;
    }

    public AnthropicNativeChatModel(AnthropicClient client, String defaultModel,
                                    Duration defaultTimeout, int maxTokens,
                                    boolean cacheAutomatically) {
        this(client, defaultModel, defaultTimeout, maxTokens,
             cacheAutomatically ? CachePolicy.SYSTEM_AND_TOOLS : CachePolicy.NONE);
    }

    public AnthropicNativeChatModel(AnthropicClient client, String defaultModel,
                                    Duration defaultTimeout, int maxTokens) {
        this(client, defaultModel, defaultTimeout, maxTokens, CachePolicy.SYSTEM_AND_TOOLS);
    }

    public AnthropicNativeChatModel(AnthropicClient client, String defaultModel,
                                    Duration defaultTimeout) {
        this(client, defaultModel, defaultTimeout, 8192, CachePolicy.SYSTEM_AND_TOOLS);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String modelName = request.parameters().modelName() != null
                           ? request.parameters().modelName()
                           : defaultModel;

        var systemBlocks = LangChain4jAnthropicMapper.extractSystemBlocks(request.messages(), cachePolicy);
        var messages     = LangChain4jAnthropicMapper.toAnthropicMessages(request.messages());

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
            var anthropicTools = LangChain4jAnthropicMapper.toAnthropicTools(tools, cachePolicy);
            paramsBuilder.tools(anthropicTools.stream()
                                              .map(ToolUnion::ofTool)
                                              .toList());
        }

        Message response = client.messages().create(paramsBuilder.build());
        return LangChain4jAnthropicMapper.toChatResponse(response);
    }

    public String defaultModel() {return defaultModel;}
}
