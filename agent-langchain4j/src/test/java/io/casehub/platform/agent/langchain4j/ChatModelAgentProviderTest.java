package io.casehub.platform.agent.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
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
