package io.casehub.platform.agent.claude;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test requiring a real Claude CLI. Skipped in CI by default.
 *
 * <p>To enable locally: set {@code CLAUDE_AGENT_TESTS_ENABLED=true} and ensure
 * {@code claude} is installed and authenticated.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "CLAUDE_AGENT_TESTS_ENABLED", matches = "true")
class ClaudeAgentClientIT {

    @Inject
    AgentProvider agentProvider;

    @Test
    void invoke_returnsAtLeastOneTextDelta() {
        var config = AgentSessionConfig.of(
                "You are a concise assistant.",
                "Say 'hello' and nothing else.");

        List<AgentEvent> events = agentProvider.invoke(config)
                .collect().asList()
                .await().atMost(Duration.ofMinutes(2));

        assertThat(events).isNotEmpty();
        assertThat(events).allSatisfy(e ->
                assertThat(e).isInstanceOf(AgentEvent.TextDelta.class));

        String fullText = events.stream()
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .collect(Collectors.joining());
        assertThat(fullText.toLowerCase()).contains("hello");
    }
}
