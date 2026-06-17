package io.casehub.platform.agent.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.AgentSessionLimitException;
import io.casehub.platform.agent.AgentTimeoutException;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClaudeAgentChatModelTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    static Instance<ChatModelListener> emptyListeners() {
        Instance<ChatModelListener> stub = mock(Instance.class);
        when(stub.stream()).thenReturn(Stream.empty());
        return stub;
    }

    @SuppressWarnings("unchecked")
    static Instance<ChatModelListener> singleListener(ChatModelListener listener) {
        Instance<ChatModelListener> stub = mock(Instance.class);
        when(stub.stream()).thenReturn(Stream.of(listener));
        return stub;
    }

    static ClaudeAgentLangchain4jProperties testProps() {
        return new ClaudeAgentLangchain4jProperties() {
            @Override public Duration closeTimeout() { return Duration.ofSeconds(5); }
        };
    }

    static Multi<AgentEvent> textDeltas(String... texts) {
        List<AgentEvent> events = new ArrayList<>();
        for (String t : texts) events.add(new AgentEvent.TextDelta(t));
        return Multi.createFrom().iterable(events);
    }

    static AgentProvider fakeProvider(AgentSession sessionToReturn) {
        return new AgentProvider() {
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig c) { return Multi.createFrom().empty(); }
            @Override public AgentSession openSession(AgentSessionInit init) { return sessionToReturn; }
        };
    }

    static AgentProvider limitedProvider() {
        return new AgentProvider() {
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig c) { return Multi.createFrom().empty(); }
            @Override public AgentSession openSession(AgentSessionInit init) {
                throw new AgentSessionLimitException(1);
            }
        };
    }

    static AgentSession fakeSession(java.util.function.Function<String, Multi<AgentEvent>> turnFactory) {
        return new AgentSession() {
            @Override public Multi<AgentEvent> query(String prompt) { return turnFactory.apply(prompt); }
            @Override public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
            @Override public void close(Duration d) {}
        };
    }

    static ChatRequest single(String text) {
        return ChatRequest.builder().messages(List.of(UserMessage.from(text))).build();
    }

    static ChatRequest withSystem(String system, String user) {
        return ChatRequest.builder()
            .messages(List.of(SystemMessage.from(system), UserMessage.from(user)))
            .build();
    }

    ClaudeAgentChatModel model(AgentProvider provider) {
        return new ClaudeAgentChatModel(provider, emptyListeners(), testProps());
    }

    // ── Contract ──────────────────────────────────────────────────────────────

    @Test
    void provider_returnsANTHROPIC() {
        assertThat(model(fakeProvider(fakeSession(__ -> Multi.createFrom().empty()))).provider())
            .isEqualTo(ModelProvider.ANTHROPIC);
    }

    @Test
    void supportedCapabilities_isEmpty() {
        assertThat(model(fakeProvider(fakeSession(__ -> Multi.createFrom().empty()))).supportedCapabilities())
            .isEmpty();
    }

    @Test
    void listeners_returnsInjectedListeners() {
        ChatModelListener mock = mock(ChatModelListener.class);
        ClaudeAgentChatModel m = new ClaudeAgentChatModel(
            fakeProvider(fakeSession(__ -> Multi.createFrom().empty())),
            singleListener(mock),
            testProps());
        assertThat(m.listeners()).containsExactly(mock);
    }

    // ── Blocking doChat ───────────────────────────────────────────────────────

    @Test
    void doChat_blocking_happyPath() {
        ClaudeAgentChatModel m = model(fakeProvider(fakeSession(__ -> textDeltas("Hello", " world"))));
        ChatResponse response = m.chat(single("hi"));
        assertThat(response.aiMessage().text()).isEqualTo("Hello world");
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void doChat_blocking_systemPromptPassedToSession() {
        List<String> capturedSystems = new ArrayList<>();
        AgentProvider provider = new AgentProvider() {
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig c) { return Multi.createFrom().empty(); }
            @Override public AgentSession openSession(AgentSessionInit init) {
                capturedSystems.add(init.systemPrompt());
                return fakeSession(__ -> textDeltas("ok"));
            }
        };
        model(provider).chat(withSystem("You are helpful", "query"));
        assertThat(capturedSystems).containsExactly("You are helpful");
    }

    @Test
    void doChat_blocking_noSystemMessage_usesEmptyPrompt() {
        List<String> capturedSystems = new ArrayList<>();
        AgentProvider provider = new AgentProvider() {
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig c) { return Multi.createFrom().empty(); }
            @Override public AgentSession openSession(AgentSessionInit init) {
                capturedSystems.add(init.systemPrompt());
                return fakeSession(__ -> textDeltas("ok"));
            }
        };
        model(provider).chat(single("just a user message"));
        assertThat(capturedSystems).containsExactly("");
    }

    @Test
    void doChat_blocking_sessionClosedAfterSuccess() {
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AgentSession session = new AgentSession() {
            @Override public Multi<AgentEvent> query(String p) { return textDeltas("done"); }
            @Override public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
            @Override public void close(Duration d) { closeCalled.set(true); }
        };
        model(fakeProvider(session)).chat(single("hi"));
        assertThat(closeCalled.get()).isTrue();
    }

    @Test
    void doChat_blocking_sessionClosedAfterFailure() {
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AgentSession session = new AgentSession() {
            @Override public Multi<AgentEvent> query(String p) {
                return Multi.createFrom().failure(new RuntimeException("subprocess died"));
            }
            @Override public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
            @Override public void close(Duration d) { closeCalled.set(true); }
        };
        assertThatThrownBy(() -> model(fakeProvider(session)).chat(single("hi")))
            .isInstanceOf(RuntimeException.class);
        assertThat(closeCalled.get()).isTrue();
    }

    @Test
    void doChat_blocking_agentTimeoutException_propagates() {
        AgentSession session = new AgentSession() {
            @Override public Multi<AgentEvent> query(String p) {
                return Multi.createFrom().failure(new AgentTimeoutException(Duration.ofSeconds(1)));
            }
            @Override public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
            @Override public void close(Duration d) {}
        };
        assertThatThrownBy(() -> model(fakeProvider(session)).chat(single("hi")))
            .isInstanceOf(AgentTimeoutException.class);
    }

    @Test
    void doChat_blocking_agentProcessException_propagates() {
        AgentSession session = new AgentSession() {
            @Override public Multi<AgentEvent> query(String p) {
                return Multi.createFrom().failure(
                    new io.casehub.platform.agent.AgentProcessException("subprocess failed", new RuntimeException("cause")));
            }
            @Override public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
            @Override public void close(Duration d) {}
        };
        assertThatThrownBy(() -> model(fakeProvider(session)).chat(single("hi")))
            .isInstanceOf(io.casehub.platform.agent.AgentProcessException.class);
    }

    @Test
    void doChat_blocking_noUserMessage_throws() {
        ClaudeAgentChatModel m = model(fakeProvider(fakeSession(__ -> Multi.createFrom().empty())));
        ChatRequest request = ChatRequest.builder()
            .messages(List.of(SystemMessage.from("system only")))
            .build();
        assertThatThrownBy(() -> m.doChat(request)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void doChat_blocking_multimodalUserMessage_throws() {
        ClaudeAgentChatModel m = model(fakeProvider(fakeSession(__ -> Multi.createFrom().empty())));
        ChatRequest request = ChatRequest.builder()
            .messages(List.of(UserMessage.from(
                dev.langchain4j.data.message.TextContent.from("text1"),
                dev.langchain4j.data.message.TextContent.from("text2"))))
            .build();
        assertThatThrownBy(() -> m.doChat(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("multimodal");
    }

    @Test
    void doChat_blocking_jsonFormat_throws() {
        ClaudeAgentChatModel m = model(fakeProvider(fakeSession(__ -> Multi.createFrom().empty())));
        ChatRequest request = ChatRequest.builder()
            .messages(List.of(UserMessage.from("hi")))
            .responseFormat(ResponseFormat.builder().type(ResponseFormatType.JSON).build())
            .build();
        assertThatThrownBy(() -> m.doChat(request)).isInstanceOf(UnsupportedFeatureException.class);
    }

    @Test
    void doChat_blocking_aiMessagePresent_throws() {
        ClaudeAgentChatModel m = model(fakeProvider(fakeSession(__ -> Multi.createFrom().empty())));
        ChatRequest request = ChatRequest.builder()
            .messages(List.of(UserMessage.from("hi"), AiMessage.from("reply"), UserMessage.from("bye")))
            .build();
        assertThatThrownBy(() -> m.doChat(request)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void doChat_blocking_multipleUserMessages_throws() {
        ClaudeAgentChatModel m = model(fakeProvider(fakeSession(__ -> Multi.createFrom().empty())));
        ChatRequest request = ChatRequest.builder()
            .messages(List.of(UserMessage.from("one"), UserMessage.from("two")))
            .build();
        assertThatThrownBy(() -> m.doChat(request)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void doChat_blocking_sessionLimitExceeded_throws() {
        // Blocking path propagates AgentSessionLimitException directly (no handler).
        // Asymmetric with streaming, where it routes to handler.onError().
        assertThatThrownBy(() -> model(limitedProvider()).chat(single("q")))
            .isInstanceOf(AgentSessionLimitException.class);
    }

    // ── Streaming doChat ──────────────────────────────────────────────────────

    @Test
    void doChat_streaming_happyPath() {
        ClaudeAgentChatModel m = model(fakeProvider(fakeSession(__ -> textDeltas("a", "b", "c"))));
        List<String> partials = new ArrayList<>();
        ChatResponse[] completed = {null};

        m.chat(single("q"), new StreamingChatResponseHandler() {
            @Override public void onPartialResponse(String t) { partials.add(t); }
            @Override public void onCompleteResponse(ChatResponse r) { completed[0] = r; }
            @Override public void onError(Throwable t) { throw new RuntimeException(t); }
        });

        await().untilAsserted(() -> assertThat(completed[0]).isNotNull());
        assertThat(partials).containsExactly("a", "b", "c");
        assertThat(completed[0].aiMessage().text()).isEqualTo("abc");
        assertThat(completed[0].finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void doChat_streaming_sessionClosedInOnCompletion() {
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AgentSession session = new AgentSession() {
            @Override public Multi<AgentEvent> query(String p) { return Multi.createFrom().empty(); }
            @Override public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
            @Override public void close(Duration d) { closeCalled.set(true); }
        };
        AtomicBoolean done = new AtomicBoolean(false);
        model(fakeProvider(session)).chat(single("q"), new StreamingChatResponseHandler() {
            @Override public void onPartialResponse(String t) {}
            @Override public void onCompleteResponse(ChatResponse r) { done.set(true); }
            @Override public void onError(Throwable t) {}
        });
        await().until(done::get);
        assertThat(closeCalled.get()).isTrue();
    }

    @Test
    void doChat_streaming_sessionClosedInOnFailure() {
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AgentSession session = new AgentSession() {
            @Override public Multi<AgentEvent> query(String p) {
                return Multi.createFrom().failure(new RuntimeException("error"));
            }
            @Override public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
            @Override public void close(Duration d) { closeCalled.set(true); }
        };
        AtomicBoolean errorReceived = new AtomicBoolean(false);
        model(fakeProvider(session)).chat(single("q"), new StreamingChatResponseHandler() {
            @Override public void onPartialResponse(String t) {}
            @Override public void onCompleteResponse(ChatResponse r) {}
            @Override public void onError(Throwable t) { errorReceived.set(true); }
        });
        await().until(errorReceived::get);
        assertThat(closeCalled.get()).isTrue();
    }

    @Test
    void doChat_streaming_sessionLimitExceeded_routesToHandlerError() {
        AtomicBoolean errorReceived = new AtomicBoolean(false);
        model(limitedProvider()).chat(single("q"), new StreamingChatResponseHandler() {
            @Override public void onPartialResponse(String t) {}
            @Override public void onCompleteResponse(ChatResponse r) {}
            @Override public void onError(Throwable t) { errorReceived.set(true); }
        });
        assertThat(errorReceived.get()).isTrue();
    }

    @Test
    void doChat_streaming_jsonFormat_throwsSynchronously() {
        ClaudeAgentChatModel m = model(fakeProvider(fakeSession(__ -> Multi.createFrom().empty())));
        ChatRequest request = ChatRequest.builder()
            .messages(List.of(UserMessage.from("hi")))
            .responseFormat(ResponseFormat.builder().type(ResponseFormatType.JSON).build())
            .build();
        assertThatThrownBy(() ->
            m.doChat(request, new StreamingChatResponseHandler() {
                @Override public void onPartialResponse(String t) {}
                @Override public void onCompleteResponse(ChatResponse r) {}
                @Override public void onError(Throwable t) {}
            })
        ).isInstanceOf(UnsupportedFeatureException.class);
    }

    @Test
    void doChat_streaming_aiMessagePresent_throwsSynchronously() {
        ClaudeAgentChatModel m = model(fakeProvider(fakeSession(__ -> Multi.createFrom().empty())));
        ChatRequest request = ChatRequest.builder()
            .messages(List.of(UserMessage.from("hi"), AiMessage.from("reply"), UserMessage.from("bye")))
            .build();
        assertThatThrownBy(() ->
            m.doChat(request, new StreamingChatResponseHandler() {
                @Override public void onPartialResponse(String t) {}
                @Override public void onCompleteResponse(ChatResponse r) {}
                @Override public void onError(Throwable t) {}
            })
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void doChat_streaming_closedSession_throwsSynchronously() {
        // AgentSession.query() throws synchronously before returning any Multi when CLOSED.
        // This propagates from doChat() to the chat() caller — NOT to handler.onError().
        AgentSession closedSession = new AgentSession() {
            @Override public Multi<AgentEvent> query(String p) {
                throw new IllegalStateException("session is closed");
            }
            @Override public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
            @Override public void close(Duration d) {}
        };
        assertThatThrownBy(() ->
            model(fakeProvider(closedSession)).doChat(single("q"), new StreamingChatResponseHandler() {
                @Override public void onPartialResponse(String t) {}
                @Override public void onCompleteResponse(ChatResponse r) {}
                @Override public void onError(Throwable t) {}
            })
        ).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("session is closed");
    }

    @Test
    void doChat_streaming_agentTimeoutException_routesToHandlerError() {
        AgentSession session = new AgentSession() {
            @Override public Multi<AgentEvent> query(String p) {
                return Multi.createFrom().failure(new AgentTimeoutException(Duration.ofSeconds(1)));
            }
            @Override public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
            @Override public void close(Duration d) {}
        };
        AtomicReference<Throwable> capturedError = new AtomicReference<>();
        model(fakeProvider(session)).chat(single("q"), new StreamingChatResponseHandler() {
            @Override public void onPartialResponse(String t) {}
            @Override public void onCompleteResponse(ChatResponse r) {}
            @Override public void onError(Throwable t) { capturedError.set(t); }
        });
        await().until(() -> capturedError.get() != null);
        assertThat(capturedError.get()).isInstanceOf(AgentTimeoutException.class);
    }

    @Test
    void doChat_streaming_agentProcessException_routesToHandlerError() {
        AgentSession session = new AgentSession() {
            @Override public Multi<AgentEvent> query(String p) {
                return Multi.createFrom().failure(
                    new io.casehub.platform.agent.AgentProcessException("subprocess failed", new RuntimeException("cause")));
            }
            @Override public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
            @Override public void close(Duration d) {}
        };
        AtomicReference<Throwable> capturedError = new AtomicReference<>();
        model(fakeProvider(session)).chat(single("q"), new StreamingChatResponseHandler() {
            @Override public void onPartialResponse(String t) {}
            @Override public void onCompleteResponse(ChatResponse r) {}
            @Override public void onError(Throwable t) { capturedError.set(t); }
        });
        await().until(() -> capturedError.get() != null);
        assertThat(capturedError.get()).isInstanceOf(io.casehub.platform.agent.AgentProcessException.class);
    }

    // ── Listener telemetry ────────────────────────────────────────────────────

    @Test
    void doChat_listenerEmpty_noTelemetryFired() {
        ClaudeAgentChatModel m = new ClaudeAgentChatModel(
            fakeProvider(fakeSession(__ -> textDeltas("ok"))),
            emptyListeners(),
            testProps());
        assertThatNoException().isThrownBy(() -> m.chat(single("hi")));
    }

    @Test
    void doChat_listenerPresent_onRequestAndOnResponseCalled() {
        ChatModelListener listener = mock(ChatModelListener.class);
        ClaudeAgentChatModel m = new ClaudeAgentChatModel(
            fakeProvider(fakeSession(__ -> textDeltas("response text"))),
            singleListener(listener),
            testProps());
        m.chat(single("hello"));
        verify(listener).onRequest(any(ChatModelRequestContext.class));
        verify(listener).onResponse(any(ChatModelResponseContext.class));
        verifyNoMoreInteractions(listener);
    }
}
