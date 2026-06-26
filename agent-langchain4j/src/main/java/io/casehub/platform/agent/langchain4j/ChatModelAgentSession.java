package io.casehub.platform.agent.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class ChatModelAgentSession implements AgentSession {

    private enum State { IDLE, ACTIVE, CLOSED }

    private final ChatModel chatModel;
    private final String systemPrompt;
    private final ChatMemory memory;
    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

    ChatModelAgentSession(ChatModel chatModel, AgentSessionInit init,
                          AgentLangchain4jProperties properties) {
        this.chatModel = chatModel;
        this.systemPrompt = init.systemPrompt();
        this.memory = MessageWindowChatMemory.withMaxMessages(properties.sessionMemoryWindowSize());
    }

    @Override
    public Multi<AgentEvent> query(String prompt) {
        if (!state.compareAndSet(State.IDLE, State.ACTIVE)) {
            State current = state.get();
            throw new IllegalStateException(current == State.CLOSED
                ? "session is closed"
                : "a turn is already active — wait for it to complete or call interrupt()");
        }
        return Multi.createFrom().item(() -> {
            memory.add(UserMessage.from(prompt));
            List<ChatMessage> messages = new ArrayList<>();
            if (!systemPrompt.isEmpty()) {
                messages.add(SystemMessage.from(systemPrompt));
            }
            messages.addAll(memory.messages());
            ChatRequest request = ChatRequest.builder().messages(messages).build();
            ChatResponse response = chatModel.chat(request);
            String text = response.aiMessage().text();
            memory.add(AiMessage.from(text != null ? text : ""));
            return (AgentEvent) new AgentEvent.TextDelta(text != null ? text : "");
        }).onTermination().invoke(() -> state.compareAndSet(State.ACTIVE, State.IDLE));
    }

    @Override
    public Uni<Void> interrupt() {
        return Uni.createFrom().voidItem();
    }

    @Override
    public void close(Duration maxWait) {
        state.set(State.CLOSED);
    }
}
