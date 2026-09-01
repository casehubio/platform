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
}
