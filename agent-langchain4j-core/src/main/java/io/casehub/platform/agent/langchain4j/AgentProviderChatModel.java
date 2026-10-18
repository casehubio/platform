package io.casehub.platform.agent.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;

import java.util.List;
import java.util.Set;

/**
 * Wraps an {@code AgentProvider} as a LangChain4j {@code ChatModel}.
 *
 * @deprecated This module is scheduled for removal. Use {@code AgentProvider} directly
 * with {@code AgentProvider.invokeBlocking()} and {@code ResponseFormat}
 * for structured output. See casehubio/platform#638.
 */
@Deprecated(forRemoval = true)
@SuppressWarnings("removal")
public class AgentProviderChatModel implements ChatModel, StreamingChatModel {

    private final AgentProvider              agentProvider;
    private final List<ChatModelListener>    listeners;
    private final AgentLangchain4jProperties properties;

    public AgentProviderChatModel(AgentProvider agentProvider,
                                  List<ChatModelListener> listeners,
                                  AgentLangchain4jProperties properties) {
        this.agentProvider = agentProvider;
        this.listeners     = listeners;
        this.properties    = properties;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OTHER;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return Set.of();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return DefaultChatRequestParameters.EMPTY;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        String             systemPrompt = extractSystemPrompt(request.messages());
        String             userMessage  = extractUserText(request.messages());
        AgentSessionConfig config       = applyResponseFormat(request, AgentSessionConfig.of(systemPrompt, userMessage));
        List<AgentEvent> events = agentProvider.invoke(config)
                                               .collect().asList()
                                               .await().atMost(properties.closeTimeout());

        StringBuilder                 text       = new StringBuilder();
        AgentEvent.InvocationComplete completion = null;
        for (AgentEvent event : events) {
            if (event instanceof AgentEvent.TextDelta td) {
                text.append(td.text());
            } else if (event instanceof AgentEvent.InvocationComplete ic) {
                completion = ic;
            }
        }

        var builder = ChatResponse.builder()
                                  .aiMessage(AiMessage.from(text.toString()));
        if (completion != null) {
            builder.metadata(CaseHubChatResponseMetadata.from(completion));
        } else {
            builder.finishReason(FinishReason.STOP);
        }
        return builder.build();
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        String             systemPrompt = extractSystemPrompt(request.messages());
        String             userMessage  = extractUserText(request.messages());
        AgentSessionConfig config       = applyResponseFormat(request, AgentSessionConfig.of(systemPrompt, userMessage));
        AgentEventBridge.dispatch(agentProvider.invoke(config), handler);
    }

    private static String extractSystemPrompt(List<ChatMessage> messages) {
        return SystemMessage.findFirst(messages).map(SystemMessage::text).orElse("");
    }

    static String extractUserText(List<ChatMessage> messages) {
        for (ChatMessage m : messages) {
            if (m instanceof AiMessage) {
                throw new IllegalArgumentException(
                        "AgentProvider-backed ChatModel does not accept AiMessage — " +
                        "session history is managed opaquely by the agent. " +
                        "Each call must supply only a SystemMessage + UserMessage.");
            }
        }
        List<UserMessage> userMessages = messages.stream()
                                                 .filter(m -> m instanceof UserMessage)
                                                 .map(m -> (UserMessage) m)
                                                 .toList();
        if (userMessages.size() > 1) {
            throw new IllegalArgumentException(
                    "AgentProvider-backed ChatModel accepts exactly one UserMessage per call.");
        }
        if (userMessages.isEmpty()) {
            throw new IllegalArgumentException("ChatRequest must contain at least one UserMessage");
        }
        try {
            return userMessages.get(0).singleText();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "This adapter supports text-only UserMessage; " +
                    "multimodal content is not supported.", e);
        }
    }

    static AgentSessionConfig applyResponseFormat(ChatRequest request, AgentSessionConfig config) {
        ResponseFormat format = request.responseFormat();
        if (format == null || format.type() != ResponseFormatType.JSON) {
            return config;
        }
        if (format.jsonSchema() == null) {
            return config.withResponseFormat(
                    io.casehub.platform.agent.ResponseFormat.jsonAny());
        }
        try {
            java.util.Map<String, Object> schemaMap =
                    dev.langchain4j.internal.JsonSchemaElementJsonUtils.toMap(format.jsonSchema().rootElement());
            var    objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String schemaJson   = objectMapper.writeValueAsString(schemaMap);
            return config.withResponseFormat(
                    io.casehub.platform.agent.ResponseFormat.json(
                            format.jsonSchema().name(), schemaJson));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize JSON schema: " + e.getMessage(), e);
        }
    }

}
