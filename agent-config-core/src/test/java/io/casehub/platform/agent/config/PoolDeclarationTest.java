package io.casehub.platform.agent.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoolDeclarationTest {

    @Test
    void validDeclaration() {
        var pool = new PoolDeclaration("review-pool", "code-reviewer", "claudony",
                2, 8, "~/workspace/reviews",
                Map.of("type", "target-tracking", "target", 0.7),
                Map.of("model-chain", java.util.List.of("opus", "sonnet")));
        assertThat(pool.name()).isEqualTo("review-pool");
        assertThat(pool.agentId()).isEqualTo("code-reviewer");
        assertThat(pool.backend()).isEqualTo("claudony");
        assertThat(pool.minActive()).isEqualTo(2);
        assertThat(pool.maxActive()).isEqualTo(8);
        assertThat(pool.workingDir()).isEqualTo("~/workspace/reviews");
        assertThat(pool.scaling()).containsEntry("type", "target-tracking");
        assertThat(pool.extensions()).containsKey("model-chain");
    }

    @Test
    void nullAgentIdThrows() {
        assertThatThrownBy(() -> new PoolDeclaration("p", null, null, 0, 1, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("agent-id");
    }

    @Test
    void negativeMinActiveThrows() {
        assertThatThrownBy(() -> new PoolDeclaration("p", "a", null, -1, 1, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("min-active");
    }

    @Test
    void zeroMaxActiveThrows() {
        assertThatThrownBy(() -> new PoolDeclaration("p", "a", null, 0, 0, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-active");
    }

    @Test
    void maxActiveLessThanMinActiveThrows() {
        assertThatThrownBy(() -> new PoolDeclaration("p", "a", null, 5, 3, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-active");
    }

    @Test
    void nullScalingDefaultsToEmptyMap() {
        var pool = new PoolDeclaration("p", "a", null, 0, 1, null, null, null);
        assertThat(pool.scaling()).isEmpty();
        assertThat(pool.extensions()).isEmpty();
    }

    @Test
    void scalingAndExtensionsAreDefensivelyCopied() {
        var scaling = new java.util.HashMap<String, Object>();
        scaling.put("type", "none");
        var pool = new PoolDeclaration("p", "a", null, 0, 1, null, scaling, null);
        assertThatThrownBy(() -> pool.scaling().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void poolDeclaration_withClearingSalienceAndPromptLimit() {
        var clearing = Map.<String, Object>of("policy", "size-aware", "threshold", 8000);
        var pool = new PoolDeclaration("voice", "voice-agent", "claude",
                                       2, 10, null, Map.of(), Map.of(),
                                       1, clearing, 50000);
        assertThat(pool.salience()).isEqualTo(1);
        assertThat(pool.clearing()).containsEntry("policy", "size-aware");
        assertThat(pool.clearing()).containsEntry("threshold", 8000);
        assertThat(pool.maxPromptTokens()).isEqualTo(50000);
    }

    @Test
    void poolDeclaration_defaultsForNewFields() {
        var pool = new PoolDeclaration("basic", "agent", "claude",
                                       0, 5, null, Map.of(), Map.of());
        assertThat(pool.salience()).isZero();
        assertThat(pool.clearing()).isNull();
        assertThat(pool.maxPromptTokens()).isZero();
    }

    @Test
    void clearingIsDefensivelyCopied() {
        var clearing = new java.util.HashMap<String, Object>();
        clearing.put("policy", "every-call");
        var pool = new PoolDeclaration("p", "a", null, 0, 1, null, null, null,
                                       0, clearing, 0);
        assertThatThrownBy(() -> pool.clearing().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullClearingRemainsNull() {
        var pool = new PoolDeclaration("p", "a", null, 0, 1, null, null, null,
                                       0, null, 0);
        assertThat(pool.clearing()).isNull();
    }

    @Test
    void negativeSalienceThrows() {
        assertThatThrownBy(() -> new PoolDeclaration("p", "a", null, 0, 1, null, null, null,
                                                     -1, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("salience");
    }

    @Test
    void negativeMaxPromptTokensThrows() {
        assertThatThrownBy(() -> new PoolDeclaration("p", "a", null, 0, 1, null, null, null,
                                                     0, null, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPromptTokens");
    }

}
