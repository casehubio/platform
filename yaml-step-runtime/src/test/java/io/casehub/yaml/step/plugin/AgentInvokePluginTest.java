package io.casehub.yaml.step.plugin;

import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentInvocationResult;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentInvokePluginTest {

    private final AgentInvokePlugin plugin = new AgentInvokePlugin();

    @Test
    void invokesAgentAndCapturesResult() {
        var provider = mockProvider("Moderate risk: 60% equities.");
        var services = new MapServiceRegistry()
                .register(AgentProvider.class, provider);

        Result result = plugin.execute(Map.of("prompt", "What's my risk profile?"), services);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output().get("text")).isEqualTo("Moderate risk: 60% equities.");
    }

    @Test
    void failsWithoutProvider() {
        var services = new MapServiceRegistry();
        Result result = plugin.execute(Map.of("prompt", "hello"), services);
        assertThat(result.isSuccess()).isFalse();
        assertThat(((Result.Failure) result).message()).contains("No AgentProvider");
    }

    @Test
    void failsWithoutPrompt() {
        var provider = mockProvider("response");
        var services = new MapServiceRegistry()
                .register(AgentProvider.class, provider);

        Result result = plugin.execute(Map.of(), services);
        assertThat(result.isSuccess()).isFalse();
        assertThat(((Result.Failure) result).message()).contains("prompt");
    }

    @Test
    void capturesTokensAndDuration() {
        var provider = mockProvider("response text");
        var services = new MapServiceRegistry()
                .register(AgentProvider.class, provider);

        Result result = plugin.execute(Map.of("prompt", "test"), services);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsKey("tokens");
        assertThat(result.output()).containsKey("duration");
    }

    private AgentProvider mockProvider(String responseText) {
        var provider = mock(AgentProvider.class);
        var invResult = new AgentInvocationResult(responseText, 10, 50, 0, 0, 0, 0.001, 100, "test", null);
        when(provider.invokeBlocking(isNull(), any(String.class), any(Duration.class)))
                .thenReturn(invResult);
        return provider;
    }
}
