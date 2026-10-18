package io.casehub.platform.agent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AgentSessionInitTest {

    @Test
    void of_requiresSystemPrompt() {
        assertThatNullPointerException()
            .isThrownBy(() -> AgentSessionInit.of(null));
    }

    @Test
    void of_setsSystemPrompt_leavesRestAtDefaults() {
        final var init = AgentSessionInit.of("my system prompt");
        assertThat(init.systemPrompt()).isEqualTo("my system prompt");
        assertThat(init.mcpServers()).isEmpty();
        assertThat(init.timeout()).isNull();
        assertThat(init.correlationId()).isNull();
    }

    @Test
    void fullConstructor_copiesDefensivelyAndNormalisesNullMcpServers() {
        final var init = new AgentSessionInit("sys", null, Duration.ofMinutes(2), "corr-1", null);
        assertThat(init.mcpServers()).isEmpty();
        assertThat(init.timeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(init.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void mcpServers_isImmutable() {
        final var mcp = new java.util.ArrayList<AgentMcpServer>();
        final var init = new AgentSessionInit("sys", mcp, null, null, null);
        mcp.add(new AgentMcpServer.Stdio("echo", List.of(), java.util.Map.of()));
        assertThat(init.mcpServers()).isEmpty();
    }

    @Test
    void modelDefaultsToNull() {
        var init = AgentSessionInit.of("sys");
        assertThat(init.model()).isNull();
    }

    @Test
    void modelSetViaFactory() {
        var init = AgentSessionInit.of("sys", "openai");
        assertThat(init.model()).isEqualTo("openai");
    }

    @Test
    void responseFormatDefaultsToNull() {
        var init = AgentSessionInit.of("sys");
        assertThat(init.responseFormat()).isNull();
    }

    @Test
    void withResponseFormatSetsFormat() {
        var init    = AgentSessionInit.of("sys");
        var format  = ResponseFormat.json("weather", "{\"type\":\"object\"}");
        var updated = init.withResponseFormat(format);
        assertThat(updated.responseFormat()).isEqualTo(format);
        assertThat(updated.systemPrompt()).isEqualTo("sys");
    }

    @Test
    void withModelPreservesResponseFormat() {
        var format  = ResponseFormat.json("weather", "{\"type\":\"object\"}");
        var init    = AgentSessionInit.of("sys").withResponseFormat(format);
        var updated = init.withModel("gpt-4");
        assertThat(updated.responseFormat()).isEqualTo(format);
        assertThat(updated.model()).isEqualTo("gpt-4");
    }
}
