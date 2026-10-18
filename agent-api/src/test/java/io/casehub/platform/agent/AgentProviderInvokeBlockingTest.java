package io.casehub.platform.agent;

import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentProviderInvokeBlockingTest {

    @Test
    void invokeBlockingCollectsTextAndMetadata() {
        AgentProvider provider = config -> Multi.createFrom().items(
                new AgentEvent.TextDelta("hello "),
                new AgentEvent.TextDelta("world"),
                new AgentEvent.InvocationComplete(
                        100, 50, 0, 20, 5, 0.003,
                        1500L, 1400L, "sess-1", 1, false, "openai", "default"));

        var result = provider.invokeBlocking(
                AgentSessionConfig.of("sys", "user"), Duration.ofSeconds(5));

        assertThat(result.text()).isEqualTo("hello world");
        assertThat(result.inputTokens()).isEqualTo(100);
        assertThat(result.outputTokens()).isEqualTo(50);
        assertThat(result.totalCostUsd()).isEqualTo(0.003);
        assertThat(result.backendKey()).isEqualTo("openai");
    }

    @Test
    void invokeBlockingWithoutCompletionEventUsesDefaults() {
        AgentProvider provider = config -> Multi.createFrom().items(
                new AgentEvent.TextDelta("just text"));

        var result = provider.invokeBlocking(
                AgentSessionConfig.of("sys", "user"), Duration.ofSeconds(5));

        assertThat(result.text()).isEqualTo("just text");
        assertThat(result.inputTokens()).isZero();
        assertThat(result.totalCostUsd()).isNull();
        assertThat(result.backendKey()).isNull();
    }

    @Test
    void invokeBlockingConvenienceOverload() {
        AgentProvider provider = config -> {
            assertThat(config.systemPrompt()).isEqualTo("sys");
            assertThat(config.userPrompt()).isEqualTo("user");
            return Multi.createFrom().items(new AgentEvent.TextDelta("ok"));
        };

        var result = provider.invokeBlocking("sys", "user", Duration.ofSeconds(5));
        assertThat(result.text()).isEqualTo("ok");
    }

    @Test
    void invokeBlockingIgnoresNonTextEvents() {
        AgentProvider provider = config -> Multi.createFrom().items(
                new AgentEvent.TextDelta("a"),
                new AgentEvent.ToolCallComplete(0, "id", "tool", "{\"x\":1}"),
                new AgentEvent.ToolResult("id", "result", false),
                new AgentEvent.TextDelta("b"));

        var result = provider.invokeBlocking(
                AgentSessionConfig.of("sys", "user"), Duration.ofSeconds(5));
        assertThat(result.text()).isEqualTo("ab");
    }

    @Test
    void invokeBlockingPropagatesFailure() {
        AgentProvider provider = config -> Multi.createFrom().failure(
                new AgentTimeoutException(Duration.ofSeconds(1)));

        assertThatThrownBy(() -> provider.invokeBlocking(
                AgentSessionConfig.of("sys", "user"), Duration.ofSeconds(5)))
                .isInstanceOf(AgentTimeoutException.class);
    }

    private interface AgentProvider extends io.casehub.platform.agent.AgentProvider {
        @Override
        default AgentSession openSession(AgentSessionInit init) {
            throw new UnsupportedOperationException();
        }
    }
}
