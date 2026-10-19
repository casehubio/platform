package io.casehub.platform.agent.router;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelLocality;
import io.casehub.platform.api.model.ModelRegistry;
import io.casehub.platform.api.model.ModelTier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutingChatModelTest {

    @Test
    void routesToCorrectBackendByModelName() {
        var openaiModel = mockChatModel("from openai");
        var claudeModel = mockChatModel("from claude");

        var registry = mock(ModelRegistry.class);
        when(registry.resolveById("gpt-4o")).thenReturn(Optional.of(descriptor("gpt-4o", "openai")));
        when(registry.resolveById("claude-sonnet-4-20250514")).thenReturn(Optional.of(descriptor("claude-sonnet-4-20250514", "claude")));

        var router = new RoutingChatModel(
                List.of(backend("openai", openaiModel), backend("claude", claudeModel)),
                registry, "claude", null);

        var request = ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .modelName("gpt-4o")
                .build();

        ChatResponse response = router.chat(request);
        assertThat(response.aiMessage().text()).isEqualTo("from openai");
        verify(openaiModel).chat(any(ChatRequest.class));
    }

    @Test
    void usesDefaultBackendWhenNoModelSpecified() {
        var claudeModel = mockChatModel("from claude");

        var registry = mock(ModelRegistry.class);

        var router = new RoutingChatModel(
                List.of(backend("claude", claudeModel)),
                registry, "claude", null);

        var request = ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .build();

        ChatResponse response = router.chat(request);
        assertThat(response.aiMessage().text()).isEqualTo("from claude");
    }

    @Test
    void usesDefaultModelFromConstructor() {
        var openaiModel = mockChatModel("from openai");

        var registry = mock(ModelRegistry.class);
        when(registry.resolveById("gpt-4o")).thenReturn(Optional.of(descriptor("gpt-4o", "openai")));

        var router = new RoutingChatModel(
                List.of(backend("openai", openaiModel)),
                registry, "openai", "gpt-4o");

        var request = ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .build();

        router.chat(request);
        verify(openaiModel).chat(any(ChatRequest.class));
    }

    @Test
    void fallsBackToDefaultWhenModelNotInRegistry() {
        var claudeModel = mockChatModel("from claude");

        var registry = mock(ModelRegistry.class);
        when(registry.resolveById("unknown-model")).thenReturn(Optional.empty());

        var router = new RoutingChatModel(
                List.of(backend("claude", claudeModel)),
                registry, "claude", null);

        var request = ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .modelName("unknown-model")
                .build();

        ChatResponse response = router.chat(request);
        assertThat(response.aiMessage().text()).isEqualTo("from claude");
    }

    @Test
    void throwsWhenNoBackendAvailable() {
        var registry = mock(ModelRegistry.class);

        var router = new RoutingChatModel(
                List.of(), registry, "claude", null);

        var request = ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .build();

        assertThatThrownBy(() -> router.chat(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No ChatModel backend available");
    }

    private static ChatModel mockChatModel(String responseText) {
        var model = mock(ChatModel.class);
        var response = ChatResponse.builder()
                .aiMessage(AiMessage.from(responseText))
                .build();
        when(model.chat(any(ChatRequest.class))).thenReturn(response);
        return model;
    }

    private static ChatModelBackend backend(String key, ChatModel model) {
        return new ChatModelBackend() {
            @Override public String key() { return key; }
            @Override public ChatModel chatModel() { return model; }
        };
    }

    private static ModelDescriptor descriptor(String id, String backendKey) {
        return new ModelDescriptor(id, id, backendKey, "default",
                backendKey, "test", id,
                ModelTier.FLAGSHIP,
                Set.of("text", "tool-use"), 128000, 16384,
                ModelLocality.CLOUD,
                null, null, null);
    }
}
