package io.casehub.yaml.core.foreach;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IterationGroupTest {

    @Test
    void list_in_returns_copy() {
        var group = new IterationGroup("region", List.of("us-east", "eu-west"));
        assertThat(group.inAsList()).containsExactly("us-east", "eu-west");
    }

    @Test
    void string_in_returns_singleton() {
        var group = new IterationGroup("region", "us-east");
        assertThat(group.inAsList()).containsExactly("us-east");
    }

    @Test
    void null_in_returns_empty() {
        var group = new IterationGroup("region", null);
        assertThat(group.inAsList()).isEmpty();
    }

    @Test
    void invalid_type_throws_at_construction() {
        assertThatThrownBy(() -> new IterationGroup("region", 42))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("list or string");
    }

// --- fromBlock factory ---

    @Test
    void fromBlock_parsesNamedGroups() {
        var block = java.util.Map.<String, Object>of(
                "regions", java.util.Map.of("as", "region", "in", java.util.List.of("us-east", "eu-west")),
                "envs", java.util.Map.of("as", "env", "in", java.util.List.of("dev", "prod")));
        var result = IterationGroup.fromBlock(block);
        assertThat(result).containsKeys("regions", "envs");
        assertThat(result.get("regions").as()).isEqualTo("region");
        assertThat(result.get("regions").inAsList()).containsExactly("us-east", "eu-west");
        assertThat(result.get("envs").as()).isEqualTo("env");
    }

    @Test
    void fromBlock_emptyBlock_returnsEmpty() {
        var result = IterationGroup.fromBlock(java.util.Map.of());
        assertThat(result).isEmpty();
    }

    @Test
    void fromBlock_skipsNonMapEntries() {
        var block = java.util.Map.<String, Object>of(
                "regions", java.util.Map.of("as", "region", "in", java.util.List.of("a")),
                "scalar", "not-a-map");
        var result = IterationGroup.fromBlock(block);
        assertThat(result).containsOnlyKeys("regions");
    }
}
