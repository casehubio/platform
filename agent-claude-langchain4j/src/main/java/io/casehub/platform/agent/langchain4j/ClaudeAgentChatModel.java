package io.casehub.platform.agent.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.AgentSessionLimitException;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Alternative
@Priority(10)
@ApplicationScoped
public class ClaudeAgentChatModel implements ChatModel, StreamingChatModel {

    private final AgentProvider agentProvider;
    private final Instance<ChatModelListener> injectedListeners;
    private final ClaudeAgentLangchain4jProperties properties;

    @Inject
    ClaudeAgentChatModel(AgentProvider agentProvider,
                         @Any Instance<ChatModelListener> listeners,
                         ClaudeAgentLangchain4jProperties properties) {
        this.agentProvider = agentProvider;
        this.injectedListeners = listeners;
        this.properties = properties;
    }

    /** For ARC proxy generation — must not be called directly. */
    protected ClaudeAgentChatModel() {
        this.agentProvider = null;
        this.injectedListeners = null;
        this.properties = null;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.ANTHROPIC;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return Set.of();
    }

    // Without this override, provider() is meaningless — ChatModel.chat() fires
    // onRequest/onResponse/onError against an empty listener list by default.
    @Override
    public List<ChatModelListener> listeners() {
        return injectedListeners.stream().toList();
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return DefaultChatRequestParameters.EMPTY;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        validateNoJsonFormat(request);
        String systemPrompt = extractSystemPrompt(request.messages());
        String userMessage = extractLastUserText(request.messages());
        AgentSession session = agentProvider.openSession(AgentSessionInit.of(systemPrompt));
        try {
            // The TextDelta filter is always true today (sealed interface, only TextDelta).
            // Retained as a forward-compatibility guard.
            String text = session.query(userMessage)
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .collect().with(Collectors.joining())
                .await().indefinitely();
            return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .finishReason(FinishReason.STOP)
                .build();
        } finally {
            session.close(properties.closeTimeout());
        }
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        validateNoJsonFormat(request);          // throws synchronously
        String systemPrompt = extractSystemPrompt(request.messages());
        String userMessage = extractLastUserText(request.messages()); // throws synchronously
        AgentSession session;
        try {
            session = agentProvider.openSession(AgentSessionInit.of(systemPrompt));
        } catch (AgentSessionLimitException e) {
            handler.onError(e);  // operational condition — route to handler
            return;
        }
        StringBuilder buffer = new StringBuilder();
        session.query(userMessage)
            .subscribe().with(
                event -> {
                    if (event instanceof AgentEvent.TextDelta delta) {
                        handler.onPartialResponse(delta.text());
                        buffer.append(delta.text());
                    }
                },
                error -> {
                    session.close(properties.closeTimeout());
                    handler.onError(error);
                },
                () -> {
                    session.close(properties.closeTimeout());
                    handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from(buffer.toString()))
                        .finishReason(FinishReason.STOP)
                        .build());
                }
            );
    }

    private static String extractSystemPrompt(List<ChatMessage> messages) {
        return SystemMessage.findFirst(messages).map(SystemMessage::text).orElse("");
    }

    private static String extractLastUserText(List<ChatMessage> messages) {
        for (ChatMessage m : messages) {
            if (m instanceof AiMessage) {
                throw new IllegalArgumentException(
                    "AgentSession-backed ChatModel adapters do not accept AiMessage — " +
                    "session history lives in the subprocess. " +
                    "Each call must supply only a single new UserMessage.");
            }
        }
        List<UserMessage> userMessages = messages.stream()
            .filter(m -> m instanceof UserMessage)
            .map(m -> (UserMessage) m)
            .toList();
        if (userMessages.size() > 1) {
            throw new IllegalArgumentException(
                "AgentSession-backed ChatModel adapters accept exactly one UserMessage per call — " +
                "multiple UserMessage elements indicate caller confusion about session state.");
        }
        if (userMessages.isEmpty()) {
            throw new IllegalArgumentException("ChatRequest must contain at least one UserMessage");
        }
        try {
            return userMessages.get(0).singleText();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                "This adapter supports text-only UserMessage; " +
                "multimodal content is not supported by the subprocess.", e);
        }
    }

    private static void validateNoJsonFormat(ChatRequest request) {
        var fmt = request.responseFormat();
        if (fmt != null && fmt.type() == ResponseFormatType.JSON) {
            throw new UnsupportedFeatureException(
                "ResponseFormat.JSON is not supported — Claude subprocess has no JSON mode; " +
                "use prompt engineering for structured output.");
        }
    }
}
