package io.casehub.yaml.step.runner;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RecoveryStateTest {

    @Test
    void initial_state_has_zero_depth() {
        var state = RecoveryState.initial(3);
        assertThat(state.depth()).isZero();
        assertThat(state.maxDepth()).isEqualTo(3);
        assertThat(state.chain()).isEmpty();
        assertThat(state.isAtLimit()).isFalse();
    }

    @Test
    void descend_increments_depth_and_adds_to_chain() {
        var state = RecoveryState.initial(3).descend("setup-a.yaml");
        assertThat(state.depth()).isEqualTo(1);
        assertThat(state.chain()).containsExactly("setup-a.yaml");
    }

    @Test
    void isAtLimit_true_when_depth_equals_maxDepth() {
        var state = RecoveryState.initial(2)
                .descend("a.yaml")
                .descend("b.yaml");
        assertThat(state.isAtLimit()).isTrue();
    }

    @Test
    void wouldCycle_detects_path_already_in_chain() {
        var state = RecoveryState.initial(5)
                .descend("a.yaml")
                .descend("b.yaml");
        assertThat(state.wouldCycle("a.yaml")).isTrue();
        assertThat(state.wouldCycle("b.yaml")).isTrue();
        assertThat(state.wouldCycle("c.yaml")).isFalse();
    }

    @Test
    void chain_preserves_insertion_order() {
        var state = RecoveryState.initial(5)
                .descend("first.yaml")
                .descend("second.yaml")
                .descend("third.yaml");
        assertThat(state.chain()).containsExactly("first.yaml", "second.yaml", "third.yaml");
    }

    @Test
    void descend_returns_new_immutable_instance() {
        var original = RecoveryState.initial(3);
        var descended = original.descend("a.yaml");
        assertThat(original.depth()).isZero();
        assertThat(original.chain()).isEmpty();
        assertThat(descended.depth()).isEqualTo(1);
    }
}
