package io.casehub.platform.agent;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class AgentSessionConfigTest {

    @Test
    void of_minimalFactory_hasExpectedValues() {
        var config = AgentSessionConfig.of("sys", "user");
        assertThat(config.systemPrompt()).isEqualTo("sys");
        assertThat(config.userPrompt()).isEqualTo("user");
        assertThat(config.mcpServers()).isEmpty();
        assertThat(config.timeout()).isNull();
        assertThat(config.correlationId()).isNull();
    }

    @Test
    void of_withTimeout_setsTimeout() {
        var timeout = Duration.ofSeconds(30);
        var config = AgentSessionConfig.of("sys", "user", timeout);
        assertThat(config.timeout()).isEqualTo(timeout);
    }

    @Test
    void compactConstructor_nullSystemPrompt_throws() {
        assertThatNullPointerException()
            .isThrownBy(() -> new AgentSessionConfig(null, "user", List.of(), null, null))
            .withMessageContaining("systemPrompt");
    }

    @Test
    void compactConstructor_nullUserPrompt_throws() {
        assertThatNullPointerException()
            .isThrownBy(() -> new AgentSessionConfig("sys", null, List.of(), null, null))
            .withMessageContaining("userPrompt");
    }

    @Test
    void compactConstructor_nullMcpServers_treatedAsEmpty() {
        var config = new AgentSessionConfig("sys", "user", null, null, null);
        assertThat(config.mcpServers()).isEmpty();
    }

    @Test
    void compactConstructor_mcpServers_defensiveCopy() {
        var servers = new ArrayList<AgentMcpServer>();
        servers.add(new AgentMcpServer.Stdio("cmd"));
        var config = new AgentSessionConfig("sys", "user", servers, null, null);
        servers.add(new AgentMcpServer.Sse("http://x"));
        assertThat(config.mcpServers()).hasSize(1);
    }
}
