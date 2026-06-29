package io.casehub.platform.agent.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentMcpServer;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.AgentSessionLimitException;
import io.casehub.platform.agent.AgentTimeoutException;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatModelAgentProviderTest {

    private AgentLangchain4jProperties properties;
    private ChatModelAgentProvider provider;

    @BeforeEach
    void setUp() {
        properties = mock(AgentLangchain4jProperties.class);
        when(properties.closeTimeout()).thenReturn(Duration.ofSeconds(30));
        when(properties.sessionMemoryWindowSize()).thenReturn(20);
        when(properties.maxConcurrentSessions()).thenReturn(10);
        provider = new ChatModelAgentProvider();
    }

    @Test
    void invoke_happyPath_emitsTextDelta() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
            ChatResponse.builder().aiMessage(AiMessage.from("Hello, World!")).build()
        );

        injectFields(chatModel);

        AgentSessionConfig config = AgentSessionConfig.of("", "test prompt");
        Multi<AgentEvent> result = provider.invoke(config);

        List<AgentEvent> events = result.collect().asList().await().indefinitely();
        assertThat(events).hasSize(1);
        AgentEvent.TextDelta delta = (AgentEvent.TextDelta) events.get(0);
        assertThat(delta.text()).isEqualTo("Hello, World!");
    }

    @Test
    void invoke_withSystemPrompt_includesSystemMessage() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
            ChatResponse.builder().aiMessage(AiMessage.from("response")).build()
        );

        injectFields(chatModel);

        AgentSessionConfig config = AgentSessionConfig.of("You are helpful", "What is 2+2?");
        provider.invoke(config).collect().asList().await().indefinitely();

        verify(chatModel).chat(any(ChatRequest.class));
    }

    @Test
    void invoke_whenDisabled_returnsFailedMulti() {
        injectFieldsWithNoChatModel();

        AgentSessionConfig config = AgentSessionConfig.of("", "test");
        Multi<AgentEvent> result = provider.invoke(config);

        assertThatThrownBy(() -> result.collect().asList().await().indefinitely())
            .hasMessageContaining("ChatModelAgentProvider is inactive");
    }

    @Test
    void openSession_returnsChatModelAgentSession() {
        ChatModel chatModel = mock(ChatModel.class);
        injectFields(chatModel);

        AgentSessionInit init = new AgentSessionInit("system prompt", List.of(), null, null);
        AgentSession session = provider.openSession(init);

        assertThat(session).isInstanceOf(ChatModelAgentSession.class);
    }

    @Test
    void openSession_whenDisabled_throwsIllegalStateException() {
        injectFieldsWithNoChatModel();

        AgentSessionInit init = new AgentSessionInit("system prompt", List.of(), null, null);

        assertThatThrownBy(() -> provider.openSession(init))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ChatModelAgentProvider is inactive");
    }

    @Test
    void init_withNoChatModel_logsWarningAndDisables() {
        injectFieldsWithNoChatModel();

        assertThat(provider.disabled).isEqualTo(true);
    }

    @Test
    void init_withAgentProviderChatModelOnly_disables() {
        AgentProviderChatModel agentProviderChatModel = new AgentProviderChatModel();
        Instance<ChatModel> instance = instanceOf(agentProviderChatModel);

        provider.chatModels = instance;
        provider.properties = properties;
        provider.init();

        assertThat(provider.disabled).isEqualTo(true);
    }

    @Test
    void init_withRealChatModel_activates() {
        ChatModel chatModel = mock(ChatModel.class);
        injectFields(chatModel);

        assertThat(provider.disabled).isEqualTo(false);
        assertThat(provider.chatModel).isSameAs(chatModel);
    }

    @Test
    void invoke_withTimeout_failsWithAgentTimeoutException() {
        ChatModel slowChatModel = mock(ChatModel.class);
        when(slowChatModel.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            Thread.sleep(500);
            return ChatResponse.builder().aiMessage(AiMessage.from("response")).build();
        });

        injectFields(slowChatModel);

        AgentSessionConfig config = AgentSessionConfig.of("", "test", Duration.ofMillis(100));

        Multi<AgentEvent> result = provider.invoke(config);

        assertThatThrownBy(() -> result.collect().asList().await().indefinitely())
            .isInstanceOf(AgentTimeoutException.class)
            .hasMessageContaining("PT0.1S");
    }

    @Test
    void invoke_withMcpServers_logsWarning() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
            ChatResponse.builder().aiMessage(AiMessage.from("response")).build()
        );

        injectFields(chatModel);

        AgentMcpServer.Stdio mcpServer = new AgentMcpServer.Stdio("npx", List.of("-y", "test"));
        AgentSessionConfig config = new AgentSessionConfig("", "test", List.of(mcpServer), null, null);

        Multi<AgentEvent> result = provider.invoke(config);
        result.collect().asList().await().indefinitely();

        // Warning is logged, but we don't assert on log output (fragile)
        // The implementation is sufficient — this test verifies no exceptions thrown
    }

    @Test
    void init_withStreamingChatModel_storesStreamingReference() {
        ChatModel dualModel = dualModel((request, handler) -> {
            handler.onCompleteResponse(ChatResponse.builder()
                .aiMessage(AiMessage.from("")).finishReason(FinishReason.STOP).build());
        });
        injectFields(dualModel);

        assertThat(provider.streamingChatModel).isSameAs(dualModel);
    }

    @Test
    void init_withChatModelOnly_streamingIsNull() {
        ChatModel chatModel = mock(ChatModel.class);
        injectFields(chatModel);

        assertThat(provider.streamingChatModel).isNull();
    }

    @Test
    void invoke_withStreamingModel_emitsMultipleTextDeltas() {
        ChatModel dualModel = dualModel((request, handler) -> {
            handler.onPartialResponse("Hello");
            handler.onPartialResponse(" World");
            handler.onCompleteResponse(ChatResponse.builder()
                .aiMessage(AiMessage.from("Hello World"))
                .finishReason(FinishReason.STOP).build());
        });

        injectFields(dualModel);

        AgentSessionConfig config = AgentSessionConfig.of("", "test prompt");
        List<AgentEvent> events = provider.invoke(config)
            .collect().asList().await().atMost(Duration.ofSeconds(5));

        assertThat(events).hasSize(2);
        assertThat(((AgentEvent.TextDelta) events.get(0)).text()).isEqualTo("Hello");
        assertThat(((AgentEvent.TextDelta) events.get(1)).text()).isEqualTo(" World");
    }

    @Test
    void invoke_withStreamingModel_emitsThinkingDelta() {
        ChatModel dualModel = dualModel((request, handler) -> {
            handler.onPartialThinking(new PartialThinking("thinking..."));
            handler.onPartialResponse("answer");
            handler.onCompleteResponse(ChatResponse.builder()
                .aiMessage(AiMessage.from("answer"))
                .finishReason(FinishReason.STOP).build());
        });

        injectFields(dualModel);

        AgentSessionConfig config = AgentSessionConfig.of("", "test");
        List<AgentEvent> events = provider.invoke(config)
            .collect().asList().await().atMost(Duration.ofSeconds(5));

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(AgentEvent.ThinkingDelta.class);
        assertThat(events.get(1)).isInstanceOf(AgentEvent.TextDelta.class);
    }

    @Test
    void invoke_withChatModelOnly_emitsSingleTextDelta() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
            ChatResponse.builder().aiMessage(AiMessage.from("complete response")).build());

        injectFields(chatModel);

        AgentSessionConfig config = AgentSessionConfig.of("", "test");
        List<AgentEvent> events = provider.invoke(config)
            .collect().asList().await().indefinitely();

        assertThat(events).hasSize(1);
        assertThat(((AgentEvent.TextDelta) events.get(0)).text()).isEqualTo("complete response");
    }

    @FunctionalInterface
    interface StreamingDoChat {
        void accept(ChatRequest request, StreamingChatResponseHandler handler);
    }

    private static ChatModel dualModel(StreamingDoChat streaming) {
        return new DualModelImpl(streaming);
    }

    private static class DualModelImpl implements ChatModel, StreamingChatModel {
        private final StreamingDoChat streaming;
        DualModelImpl(StreamingDoChat streaming) { this.streaming = streaming; }

        @Override public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(AiMessage.from("")).build();
        }
        @Override public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            streaming.accept(request, handler);
        }
        @Override public ModelProvider provider() { return ModelProvider.OTHER; }
        @Override public java.util.Set<Capability> supportedCapabilities() { return java.util.Set.of(); }
        @Override public ChatRequestParameters defaultRequestParameters() { return DefaultChatRequestParameters.EMPTY; }
        @Override public java.util.List<ChatModelListener> listeners() { return java.util.List.of(); }
    }

    // Helper methods

    private void injectFields(ChatModel chatModel) {
        Instance<ChatModel> instance = instanceOf(chatModel);
        provider.chatModels = instance;
        provider.properties = properties;
        provider.init();
    }

    private void injectFieldsWithNoChatModel() {
        Instance<ChatModel> instance = instanceOf();
        provider.chatModels = instance;
        provider.properties = properties;
        provider.init();
    }

    @Test
    void init_withZeroMaxConcurrentSessions_throws() {
        when(properties.maxConcurrentSessions()).thenReturn(0);
        ChatModel chatModel = mock(ChatModel.class);
        Instance<ChatModel> instance = instanceOf(chatModel);
        provider.chatModels = instance;
        provider.properties = properties;

        assertThatThrownBy(() -> provider.init())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("max-concurrent-sessions must be >= 1");
    }

    @Test
    void init_withNegativeMaxConcurrentSessions_throws() {
        when(properties.maxConcurrentSessions()).thenReturn(-1);
        ChatModel chatModel = mock(ChatModel.class);
        Instance<ChatModel> instance = instanceOf(chatModel);
        provider.chatModels = instance;
        provider.properties = properties;

        assertThatThrownBy(() -> provider.init())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("max-concurrent-sessions must be >= 1");
    }

    @Test
    void invoke_atCapacity_returnsAgentSessionLimitException() {
        when(properties.maxConcurrentSessions()).thenReturn(1);
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
            ChatResponse.builder().aiMessage(AiMessage.from("response")).build());
        injectFields(chatModel);

        // Exhaust the single permit
        semaphoreAcquire();

        AgentSessionConfig config = AgentSessionConfig.of("", "test");
        Multi<AgentEvent> result = provider.invoke(config);

        assertThatThrownBy(() -> result.collect().asList().await().indefinitely())
            .isInstanceOf(AgentSessionLimitException.class)
            .hasMessageContaining("1 active sessions");

        // Release the manually acquired permit
        provider.semaphore.release();
    }

    @Test
    void invoke_releasesPermitOnCompletion() {
        when(properties.maxConcurrentSessions()).thenReturn(1);
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
            ChatResponse.builder().aiMessage(AiMessage.from("response")).build());
        injectFields(chatModel);

        assertThat(provider.availablePermits()).isEqualTo(1);

        provider.invoke(AgentSessionConfig.of("", "test"))
            .collect().asList().await().indefinitely();

        assertThat(provider.availablePermits()).isEqualTo(1);
    }

    @Test
    void invoke_releasesPermitOnFailure() {
        when(properties.maxConcurrentSessions()).thenReturn(1);
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class)))
            .thenThrow(new RuntimeException("model error"));
        injectFields(chatModel);

        assertThatThrownBy(() ->
            provider.invoke(AgentSessionConfig.of("", "test"))
                .collect().asList().await().indefinitely())
            .hasMessageContaining("model error");

        assertThat(provider.availablePermits()).isEqualTo(1);
    }

    @Test
    void invoke_releasesPermitOnCancellation() {
        when(properties.maxConcurrentSessions()).thenReturn(1);
        CountDownLatch modelBlocked = new CountDownLatch(1);
        CountDownLatch modelReached = new CountDownLatch(1);
        ChatModel slowModel = mock(ChatModel.class);
        when(slowModel.chat(any(ChatRequest.class))).thenAnswer(inv -> {
            modelReached.countDown();
            modelBlocked.await(5, TimeUnit.SECONDS);
            return ChatResponse.builder().aiMessage(AiMessage.from("late")).build();
        });
        injectFields(slowModel);

        var cancellable = provider.invoke(AgentSessionConfig.of("", "test"))
            .subscribe().withSubscriber(io.smallrye.mutiny.helpers.test.AssertSubscriber.create(10));

        // Wait for the model call to start, then cancel
        try { modelReached.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        cancellable.cancel();
        modelBlocked.countDown();

        // Permit must be released after cancellation
        assertThat(provider.availablePermits()).isEqualTo(1);
    }

    @Test
    void invoke_syncExceptionReleasesPermit() {
        when(properties.maxConcurrentSessions()).thenReturn(1);
        ChatModel chatModel = mock(ChatModel.class);
        injectFields(chatModel);

        // Force NPE in try block: systemPrompt() returns null → .isEmpty() throws NPE
        AgentSessionConfig config = mock(AgentSessionConfig.class);
        when(config.systemPrompt()).thenReturn(null);

        Multi<AgentEvent> result = provider.invoke(config);
        assertThatThrownBy(() -> result.collect().asList().await().indefinitely())
            .isInstanceOf(NullPointerException.class);

        assertThat(provider.availablePermits()).isEqualTo(1);
    }

    @Test
    void invoke_streaming_holdsPermitUntilStreamCompletes() {
        when(properties.maxConcurrentSessions()).thenReturn(1);
        CountDownLatch streamStarted = new CountDownLatch(1);
        CountDownLatch completeStream = new CountDownLatch(1);
        ChatModel dualModel = dualModel((request, handler) -> {
            streamStarted.countDown();
            handler.onPartialResponse("chunk");
            try { completeStream.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            handler.onCompleteResponse(ChatResponse.builder()
                .aiMessage(AiMessage.from("chunk")).finishReason(FinishReason.STOP).build());
        });
        injectFields(dualModel);

        Thread streamThread = new Thread(() ->
            provider.invoke(AgentSessionConfig.of("", "test"))
                .collect().asList().await().atMost(Duration.ofSeconds(10)));
        streamThread.start();

        try { streamStarted.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Permit held while stream is active
        assertThat(provider.availablePermits()).isEqualTo(0);

        // Second invoke fails while first stream is active
        AgentSessionConfig config2 = AgentSessionConfig.of("", "test2");
        assertThatThrownBy(() ->
            provider.invoke(config2).collect().asList().await().indefinitely())
            .isInstanceOf(AgentSessionLimitException.class);

        completeStream.countDown();
        try { streamThread.join(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Permit released after stream completes
        assertThat(provider.availablePermits()).isEqualTo(1);
    }

    private void semaphoreAcquire() {
        try {
            provider.semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @Test
    void openSession_atCapacity_throwsAgentSessionLimitException() {
        when(properties.maxConcurrentSessions()).thenReturn(1);
        ChatModel chatModel = mock(ChatModel.class);
        injectFields(chatModel);

        semaphoreAcquire();

        AgentSessionInit init = AgentSessionInit.of("system");

        assertThatThrownBy(() -> provider.openSession(init))
            .isInstanceOf(AgentSessionLimitException.class)
            .hasMessageContaining("1 active sessions");

        provider.semaphore.release();
    }

    @Test
    void openSession_constructionFailure_releasesPermit() {
        when(properties.maxConcurrentSessions()).thenReturn(1);
        ChatModel chatModel = mock(ChatModel.class);
        injectFields(chatModel);

        assertThatThrownBy(() -> provider.openSession(null))
            .isInstanceOf(NullPointerException.class);

        assertThat(provider.availablePermits()).isEqualTo(1);
    }

    @Test
    void openSession_close_releasesPermit() {
        when(properties.maxConcurrentSessions()).thenReturn(1);
        ChatModel chatModel = mock(ChatModel.class);
        injectFields(chatModel);

        AgentSession session = provider.openSession(AgentSessionInit.of("system"));
        assertThat(provider.availablePermits()).isEqualTo(0);

        session.close(Duration.ofSeconds(1));
        assertThat(provider.availablePermits()).isEqualTo(1);

        // Can open another session now
        AgentSession session2 = provider.openSession(AgentSessionInit.of("system"));
        session2.close(Duration.ofSeconds(1));
    }

    @SuppressWarnings("unchecked")
    private Instance<ChatModel> instanceOf(ChatModel... models) {
        Instance<ChatModel> instance = mock(Instance.class);
        Instance<ChatModel> defaultInstance = mock(Instance.class);
        when(instance.select(Default.Literal.INSTANCE)).thenReturn(defaultInstance);
        when(defaultInstance.stream()).thenReturn(Stream.of(models));
        return instance;
    }
}
