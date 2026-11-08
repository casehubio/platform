package io.casehub.platform.simulation.selector;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StateMachineSelectorTest {

    @Test
    void initialStateIsActivePhase() {
        var selector = new StateMachineSelector("pending",
                Map.of("pending", Map.of("pay", "completed")));
        assertThat(selector.activePhase()).isEqualTo("pending");
    }

    @Test
    void advanceTransitionsToNewState() {
        var selector = new StateMachineSelector("pending",
                Map.of("pending", Map.of("pay", "completed"),
                       "completed", Map.of("ship", "shipped")));

        selector.advance("pay");
        assertThat(selector.activePhase()).isEqualTo("completed");

        selector.advance("ship");
        assertThat(selector.activePhase()).isEqualTo("shipped");
    }

    @Test
    void unknownTriggerDoesNotChangeState() {
        var selector = new StateMachineSelector("pending",
                Map.of("pending", Map.of("pay", "completed")));

        selector.advance("unknown");
        assertThat(selector.activePhase()).isEqualTo("pending");
    }

    @Test
    void terminalStateIgnoresAllTriggers() {
        var selector = new StateMachineSelector("pending",
                Map.of("pending", Map.of("pay", "done")));

        selector.advance("pay");
        assertThat(selector.activePhase()).isEqualTo("done");

        selector.advance("pay");
        assertThat(selector.activePhase()).isEqualTo("done");
    }

    @Test
    void multipleTransitionsFromSameState() {
        var selector = new StateMachineSelector("idle",
                Map.of("idle", Map.of("start", "running", "cancel", "cancelled")));

        selector.advance("cancel");
        assertThat(selector.activePhase()).isEqualTo("cancelled");
    }

    @Test
    void typeReturnsStateMachine() {
        var selector = new StateMachineSelector("a", Map.of("a", Map.of("go", "b")));
        assertThat(selector.type()).isEqualTo("state-machine");
    }

    @Test
    void resetRestoresInitialState() {
        var selector = new StateMachineSelector("a", Map.of(
                "a", Map.of("go", "b"),
                "b", Map.of("go", "c")));
        selector.advance("go");
        assertThat(selector.activePhase()).isEqualTo("b");

        selector.reset();
        assertThat(selector.activePhase()).isEqualTo("a");
    }
}
