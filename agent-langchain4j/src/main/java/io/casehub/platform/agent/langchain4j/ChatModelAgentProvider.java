package io.casehub.platform.agent.langchain4j;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.AgentSessionLimitException;
import io.casehub.platform.agent.AgentTimeoutException;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.concurrent.Semaphore;

@Alternative
@Priority(1)
@ApplicationScoped
public class ChatModelAgentProvider implements AgentProvider {

    private static final Logger LOG = Logger.getLogger(ChatModelAgentProvider.class);

    @Inject @Any Instance<ChatModel> chatModels;
    @Inject AgentLangchain4jProperties properties;

    ChatModel chatModel;
    StreamingChatModel streamingChatModel;
    boolean disabled;
    Semaphore semaphore;

    protected ChatModelAgentProvider() {}

    @PostConstruct
    void init() {
        int maxSessions = properties.maxConcurrentSessions();
        if (maxSessions < 1) {
            throw new IllegalStateException(
                "casehub.platform.agent.langchain4j.max-concurrent-sessions must be >= 1, got " + maxSessions);
        }
        semaphore = new Semaphore(maxSessions);

        List<ChatModel> candidates = chatModels.select(Default.Literal.INSTANCE).stream()
            .filter(m -> !(m instanceof AgentProviderChatModel))
            .toList();
        if (candidates.isEmpty()) {
            LOG.warn("ChatModelAgentProvider: no ChatModel bean available — " +
                     "add a quarkus-langchain4j provider to activate. " +
                     "AgentProvider calls will fail until a ChatModel is present.");
            disabled = true;
            return;
        }
        chatModel = candidates.get(0);
        streamingChatModel = (chatModel instanceof StreamingChatModel s) ? s : null;
    }

    int availablePermits() {
        return semaphore.availablePermits();
    }

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        if (disabled) {
            return Multi.createFrom().failure(new IllegalStateException(
                "ChatModelAgentProvider is inactive — no ChatModel bean available. " +
                "Add a quarkus-langchain4j provider (e.g. quarkus-langchain4j-openai) " +
                "to the classpath."));
        }
        if (!semaphore.tryAcquire()) {
            LOG.warnf("ChatModelAgentProvider: session limit reached (%d/%d active sessions)",
                properties.maxConcurrentSessions() - semaphore.availablePermits(),
                properties.maxConcurrentSessions());
            return Multi.createFrom().failure(
                new AgentSessionLimitException(properties.maxConcurrentSessions()));
        }
        try {
            if (!config.mcpServers().isEmpty()) {
                LOG.warnf("ChatModelAgentProvider: mcpServers ignored — LangChain4j models " +
                          "do not support MCP. %d server(s) configured but unused.",
                          config.mcpServers().size());
            }

            ChatRequest request = ChatRequest.builder()
                .messages(config.systemPrompt().isEmpty()
                    ? List.of(UserMessage.from(config.userPrompt()))
                    : List.of(SystemMessage.from(config.systemPrompt()),
                              UserMessage.from(config.userPrompt())))
                .build();

            Multi<AgentEvent> result;
            if (streamingChatModel != null) {
                result = AgentEventBridge.stream(streamingChatModel, request);
            } else {
                result = Multi.createFrom().item(() -> {
                    ChatResponse response = chatModel.chat(request);
                    String text = response.aiMessage().text();
                    return (AgentEvent) new AgentEvent.TextDelta(text != null ? text : "");
                });
            }

            if (config.timeout() != null) {
                result = result.ifNoItem().after(config.timeout()).failWith(
                    () -> new AgentTimeoutException(config.timeout()));
            }
            return result
                .onCompletion().invoke(semaphore::release)
                .onFailure().invoke(t -> semaphore.release())
                .onCancellation().invoke(semaphore::release);
        } catch (Exception e) {
            semaphore.release();
            return Multi.createFrom().failure(e);
        }
    }

    @Override
    public AgentSession openSession(AgentSessionInit init) {
        if (disabled) {
            throw new IllegalStateException(
                "ChatModelAgentProvider is inactive — no ChatModel bean available.");
        }
        if (!semaphore.tryAcquire()) {
            LOG.warnf("ChatModelAgentProvider: session limit reached (%d/%d active sessions)",
                properties.maxConcurrentSessions() - semaphore.availablePermits(),
                properties.maxConcurrentSessions());
            throw new AgentSessionLimitException(properties.maxConcurrentSessions());
        }
        try {
            return new ChatModelAgentSession(chatModel, streamingChatModel, init, properties, semaphore);
        } catch (Exception e) {
            semaphore.release();
            throw e;
        }
    }
}
