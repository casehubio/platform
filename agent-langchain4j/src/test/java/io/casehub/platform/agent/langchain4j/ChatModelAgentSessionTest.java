package io.casehub.platform.agent.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionInit;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatModelAgentSessionTest {

    /** Creates a simple ChatModel that returns the given response text. */
    private static ChatModel simpleChatModel(String responseText) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                return ChatResponse.builder()
                    .aiMessage(AiMessage.from(responseText))
                    .build();
            }
        };
    }

    @Test
    void query_happyPath_emitsTextDelta() {
        ChatModel model = simpleChatModel("Hello from AI");
        AgentSessionInit init = AgentSessionInit.of("system prompt");
        AgentLangchain4jProperties properties = new TestProperties(Duration.ofSeconds(30), 20);

        ChatModelAgentSession session = new ChatModelAgentSession(model, init, properties);

        List<String> events = session.query("user prompt")
            .onItem().transform(event -> ((AgentEvent.TextDelta) event).text())
            .collect().asList()
            .await().atMost(Duration.ofSeconds(5));

        assertThat(events).containsExactly("Hello from AI");
    }

    @Test
    void query_multiTurn_includesHistory() {
        AtomicInteger callCount = new AtomicInteger(0);
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                int call = callCount.incrementAndGet();
                if (call == 1) {
                    return ChatResponse.builder().aiMessage(AiMessage.from("Response 1")).build();
                }
                // Second call should include first exchange in history
                List<ChatMessage> messages = request.messages();
                assertThat(messages).hasSizeGreaterThan(2); // System + User1 + AI1 + User2
                return ChatResponse.builder().aiMessage(AiMessage.from("Response 2")).build();
            }
        };
        AgentSessionInit init = AgentSessionInit.of("system prompt");
        AgentLangchain4jProperties properties = new TestProperties(Duration.ofSeconds(30), 20);

        ChatModelAgentSession session = new ChatModelAgentSession(model, init, properties);

        session.query("prompt 1").collect().asList().await().atMost(Duration.ofSeconds(5));
        List<String> secondResponse = session.query("prompt 2")
            .onItem().transform(event -> ((AgentEvent.TextDelta) event).text())
            .collect().asList()
            .await().atMost(Duration.ofSeconds(5));

        assertThat(secondResponse).containsExactly("Response 2");
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void query_onClosed_throws() {
        ChatModel model = simpleChatModel("response");
        AgentSessionInit init = AgentSessionInit.of("system");
        AgentLangchain4jProperties properties = new TestProperties(Duration.ofSeconds(30), 20);

        ChatModelAgentSession session = new ChatModelAgentSession(model, init, properties);
        session.close(Duration.ofSeconds(1));

        assertThatThrownBy(() -> session.query("prompt").collect().asList().await().indefinitely())
            .hasMessageContaining("session is closed");
    }

    @Test
    void query_concurrent_throws() throws InterruptedException {
        CountDownLatch firstQueryStarted = new CountDownLatch(1);
        CountDownLatch blockFirstQuery = new CountDownLatch(1);

        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                firstQueryStarted.countDown();
                try {
                    blockFirstQuery.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ChatResponse.builder().aiMessage(AiMessage.from("response")).build();
            }
        };
        AgentSessionInit init = AgentSessionInit.of("system");
        AgentLangchain4jProperties properties = new TestProperties(Duration.ofSeconds(30), 20);

        ChatModelAgentSession session = new ChatModelAgentSession(model, init, properties);

        // Start first query in background
        Thread firstThread = new Thread(() ->
            session.query("first").collect().asList().await().indefinitely()
        );
        firstThread.start();

        firstQueryStarted.await(5, TimeUnit.SECONDS);

        // Try second query while first is active
        assertThatThrownBy(() -> session.query("second"))
            .hasMessageContaining("a turn is already active");

        blockFirstQuery.countDown();
        firstThread.join(5000);
    }

    @Test
    void interrupt_isNoOp() {
        ChatModel model = simpleChatModel("response");
        AgentSessionInit init = AgentSessionInit.of("system");
        AgentLangchain4jProperties properties = new TestProperties(Duration.ofSeconds(30), 20);

        ChatModelAgentSession session = new ChatModelAgentSession(model, init, properties);

        session.interrupt().await().atMost(Duration.ofSeconds(1));
        // Should not throw
    }

    @Test
    void close_setsClosedState() {
        ChatModel model = simpleChatModel("response");
        AgentSessionInit init = AgentSessionInit.of("system");
        AgentLangchain4jProperties properties = new TestProperties(Duration.ofSeconds(30), 20);

        ChatModelAgentSession session = new ChatModelAgentSession(model, init, properties);
        session.close(Duration.ofSeconds(1));

        assertThatThrownBy(() -> session.query("prompt").collect().asList().await().indefinitely())
            .hasMessageContaining("session is closed");
    }

    @Test
    void close_idempotent() {
        ChatModel model = simpleChatModel("response");
        AgentSessionInit init = AgentSessionInit.of("system");
        AgentLangchain4jProperties properties = new TestProperties(Duration.ofSeconds(30), 20);

        ChatModelAgentSession session = new ChatModelAgentSession(model, init, properties);
        session.close(Duration.ofSeconds(1));
        session.close(Duration.ofSeconds(1)); // Should not throw
    }

    private record TestProperties(Duration closeTimeout, int sessionMemoryWindowSize)
        implements AgentLangchain4jProperties {}
}
