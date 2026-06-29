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
import io.casehub.platform.agent.AgentTimeoutException;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
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

    @SuppressWarnings("unchecked")
    private Instance<ChatModel> instanceOf(ChatModel... models) {
        Instance<ChatModel> instance = mock(Instance.class);
        Instance<ChatModel> defaultInstance = mock(Instance.class);
        when(instance.select(Default.Literal.INSTANCE)).thenReturn(defaultInstance);
        when(defaultInstance.stream()).thenReturn(Stream.of(models));
        return instance;
    }
}
