package io.casehub.platform.agent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ResolvedAgentTest {

    @Test
    void requiredFieldsEnforced() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ResolvedAgent(null, "prompt", null, null, Map.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> new ResolvedAgent("id", null, null, null, Map.of()));
    }

    @Test
    void metadataDefaultsToEmptyMap() {
        var agent = new ResolvedAgent("id", "prompt", null, null, null);
        assertThat(agent.metadata()).isNotNull().isEmpty();
    }

    @Test
    void metadataIsDefensivelyCopied() {
        var mutable = new java.util.HashMap<String, String>();
        mutable.put("key", "value");
        var agent = new ResolvedAgent("id", "prompt", null, null, mutable);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> agent.metadata().put("new", "entry"));
    }

    @Test
    void allFieldsPreserved() {
        var agent = new ResolvedAgent("my-agent", "You are helpful", "claude-opus-4-6",
                Duration.ofSeconds(60), Map.of("slot", "prod"));
        assertThat(agent.agentId()).isEqualTo("my-agent");
        assertThat(agent.systemPrompt()).isEqualTo("You are helpful");
        assertThat(agent.model()).isEqualTo("claude-opus-4-6");
        assertThat(agent.timeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(agent.metadata()).containsEntry("slot", "prod");
    }
}
