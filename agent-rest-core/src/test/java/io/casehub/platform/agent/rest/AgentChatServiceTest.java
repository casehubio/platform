package io.casehub.platform.agent.rest;

import io.casehub.platform.agent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentChatServiceTest {

    private AgentProvider agentProvider;
    private AgentResolver agentResolver;
    private AgentChatService service;

    @BeforeEach
    void setUp() {
        agentProvider = mock(AgentProvider.class);
        agentResolver = mock(AgentResolver.class);
        service = new AgentChatService(agentProvider, agentResolver);
    }

    @Test
    void listAgentsDelegatesToResolver() {
        var descriptors = List.of(
                new AgentDescriptor("agent-1", "Agent One", "First agent", Map.of()));
        when(agentResolver.discover()).thenReturn(descriptors);

        var result = service.listAgents();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().agentId()).isEqualTo("agent-1");
    }

    @Test
    void listAgentsEmptyWhenNoResolver() {
        when(agentResolver.discover()).thenReturn(List.of());

        assertThat(service.listAgents()).isEmpty();
    }

    @Test
    void explicitModeInvokesWithSystemPrompt() {
        var request = new AgentChatRequest("Hello", "You are helpful", null, null, null);
        var result = new AgentInvocationResult("Hi there", 10, 20, 0, 0, 0,
                0.001, 500L, "claude", "default");
        when(agentProvider.invokeBlocking(any(AgentSessionConfig.class), any(Duration.class)))
                .thenReturn(result);

        var response = service.invoke(request);

        assertThat(response.text()).isEqualTo("Hi there");
        assertThat(response.agentId()).isNull();
        assertThat(response.agentMetadata()).isEmpty();
        assertThat(response.backendKey()).isEqualTo("claude");
        assertThat(response.inputTokens()).isEqualTo(10);
        assertThat(response.outputTokens()).isEqualTo(20);
        assertThat(response.totalCostUsd()).isEqualTo(0.001);
        assertThat(response.durationMs()).isEqualTo(500L);

        var configCaptor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invokeBlocking(configCaptor.capture(), any(Duration.class));
        assertThat(configCaptor.getValue().systemPrompt()).isEqualTo("You are helpful");
        assertThat(configCaptor.getValue().userPrompt()).isEqualTo("Hello");
    }

    @Test
    void explicitModeMissingSystemPromptThrows() {
        var request = new AgentChatRequest("Hello", null, null, null, null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.invoke(request))
                .withMessageContaining("systemPrompt");
    }

    @Test
    void resolvedModeUsesResolvedConfig() {
        var resolved = new ResolvedAgent("my-agent", "Resolved prompt", "claude-opus-4-6",
                Duration.ofSeconds(30), Map.of("slot", "prod"));
        when(agentResolver.resolve("my-agent")).thenReturn(Optional.of(resolved));

        var result = new AgentInvocationResult("Response", 5, 15, 0, 0, 0,
                0.002, 300L, "claude", "default");
        when(agentProvider.invokeBlocking(any(AgentSessionConfig.class), any(Duration.class)))
                .thenReturn(result);

        var request = new AgentChatRequest("Hello", null, "my-agent", null, null);
        var response = service.invoke(request);

        assertThat(response.agentId()).isEqualTo("my-agent");
        assertThat(response.agentMetadata()).containsEntry("slot", "prod");

        var configCaptor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        var timeoutCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(agentProvider).invokeBlocking(configCaptor.capture(), timeoutCaptor.capture());
        assertThat(configCaptor.getValue().systemPrompt()).isEqualTo("Resolved prompt");
        assertThat(configCaptor.getValue().model()).isEqualTo("claude-opus-4-6");
        assertThat(timeoutCaptor.getValue()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void resolvedModeUnknownAgentThrows() {
        when(agentResolver.resolve("unknown")).thenReturn(Optional.empty());

        var request = new AgentChatRequest("Hello", null, "unknown", null, null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.invoke(request))
                .withMessageContaining("unknown");
    }

    @Test
    void callerOverridesResolvedModel() {
        var resolved = new ResolvedAgent("my-agent", "Prompt", "claude-sonnet-5-5",
                null, Map.of());
        when(agentResolver.resolve("my-agent")).thenReturn(Optional.of(resolved));
        when(agentProvider.invokeBlocking(any(), any()))
                .thenReturn(new AgentInvocationResult("ok", 0, 0, 0, 0, 0, null, 0, null, null));

        var request = new AgentChatRequest("Hello", null, "my-agent", "claude-opus-4-6", null);
        service.invoke(request);

        var captor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invokeBlocking(captor.capture(), any());
        assertThat(captor.getValue().model()).isEqualTo("claude-opus-4-6");
    }

    @Test
    void callerOverridesResolvedTimeout() {
        var resolved = new ResolvedAgent("my-agent", "Prompt", null,
                Duration.ofSeconds(30), Map.of());
        when(agentResolver.resolve("my-agent")).thenReturn(Optional.of(resolved));
        when(agentProvider.invokeBlocking(any(), any()))
                .thenReturn(new AgentInvocationResult("ok", 0, 0, 0, 0, 0, null, 0, null, null));

        var request = new AgentChatRequest("Hello", null, "my-agent", null, 90L);
        service.invoke(request);

        var captor = ArgumentCaptor.forClass(Duration.class);
        verify(agentProvider).invokeBlocking(any(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void defaultTimeoutWhenNoneSpecified() {
        when(agentProvider.invokeBlocking(any(), any()))
                .thenReturn(new AgentInvocationResult("ok", 0, 0, 0, 0, 0, null, 0, null, null));

        var request = new AgentChatRequest("Hello", "System", null, null, null);
        service.invoke(request);

        var captor = ArgumentCaptor.forClass(Duration.class);
        verify(agentProvider).invokeBlocking(any(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(Duration.ofSeconds(120));
    }
}
