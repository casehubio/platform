package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrcStateMachineTest {

    enum OrderState { PENDING, APPROVED, SHIPPED, REJECTED, DELIVERED, CANCELLED }

    private DefaultOrcStateMachine<OrderState> buildOrderMachine() {
        return DefaultOrcStateMachine.builder("order", OrderState.class, OrderState.PENDING)
                .transition(OrderState.PENDING, OrderState.APPROVED)
                .transition(OrderState.PENDING, OrderState.REJECTED)
                .transition(OrderState.APPROVED, OrderState.SHIPPED)
                .transition(OrderState.APPROVED, OrderState.CANCELLED)
                .transition(OrderState.SHIPPED, OrderState.DELIVERED)
                .terminal(OrderState.REJECTED, OrderState.DELIVERED, OrderState.CANCELLED)
                .build();
    }

    @Test
    void initialState_isSet() {
        var sm = buildOrderMachine();
        assertThat(sm.currentState()).isEqualTo(OrderState.PENDING);
    }

    @Test
    void validTransition_changesState() {
        var sm = buildOrderMachine();
        boolean result = sm.transition(OrderState.PENDING, OrderState.APPROVED);
        assertThat(result).isTrue();
        assertThat(sm.currentState()).isEqualTo(OrderState.APPROVED);
    }

    @Test
    void invalidTransition_throws() {
        var sm = buildOrderMachine();
        assertThatThrownBy(() -> sm.transition(OrderState.PENDING, OrderState.SHIPPED))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("SHIPPED");
    }

    @Test
    void terminalState_rejectsTransitions() {
        var sm = buildOrderMachine();
        sm.transition(OrderState.PENDING, OrderState.REJECTED);
        assertThatThrownBy(() -> sm.transition(OrderState.REJECTED, OrderState.PENDING))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    void stateQueryable() {
        var sm = buildOrderMachine();
        sm.transition(OrderState.PENDING, OrderState.APPROVED);
        sm.transition(OrderState.APPROVED, OrderState.SHIPPED);
        assertThat(sm.currentState()).isEqualTo(OrderState.SHIPPED);
    }

    @Test
    void guardCondition_preventsTransition() {
        var sm = DefaultOrcStateMachine.builder("guarded", OrderState.class, OrderState.PENDING)
                .transition(OrderState.PENDING, OrderState.APPROVED, payload -> false)
                .build();

        boolean result = sm.transition(OrderState.PENDING, OrderState.APPROVED);
        assertThat(result).isFalse();
        assertThat(sm.currentState()).isEqualTo(OrderState.PENDING);
    }

    @Test
    void guardCondition_returnsFalse_stateUnchanged() {
        var sm = DefaultOrcStateMachine.builder("guarded", OrderState.class, OrderState.PENDING)
                .transition(OrderState.PENDING, OrderState.APPROVED, payload -> (Integer) payload > 5)
                .build();

        assertThat(sm.transition(OrderState.PENDING, OrderState.APPROVED, 3)).isFalse();
        assertThat(sm.currentState()).isEqualTo(OrderState.PENDING);
        assertThat(sm.transition(OrderState.PENDING, OrderState.APPROVED, 10)).isTrue();
        assertThat(sm.currentState()).isEqualTo(OrderState.APPROVED);
    }

    @Test
    void transitionHandler_firesAfterCommit() {
        var sm = buildOrderMachine();
        var fired = new AtomicBoolean(false);
        var capturedPayload = new AtomicReference<>();
        sm.onTransition(OrderState.PENDING, OrderState.APPROVED, payload -> {
            fired.set(true);
            capturedPayload.set(payload);
        });

        sm.transition(OrderState.PENDING, OrderState.APPROVED, "data");
        assertThat(fired.get()).isTrue();
        assertThat(capturedPayload.get()).isEqualTo("data");
    }

    @Test
    void onEnter_firesOnStateEntry() {
        var sm = buildOrderMachine();
        var entered = new AtomicBoolean(false);
        sm.onEnter(OrderState.APPROVED, () -> entered.set(true));

        sm.transition(OrderState.PENDING, OrderState.APPROVED);
        assertThat(entered.get()).isTrue();
    }

    @Test
    void onExit_firesOnStateExit() {
        var sm = buildOrderMachine();
        var exited = new AtomicBoolean(false);
        sm.onExit(OrderState.PENDING, () -> exited.set(true));

        sm.transition(OrderState.PENDING, OrderState.APPROVED);
        assertThat(exited.get()).isTrue();
    }

    @Test
    void wrongFromState_casFailsGracefully() {
        var sm = buildOrderMachine();
        boolean result = sm.transition(OrderState.APPROVED, OrderState.SHIPPED);
        assertThat(result).isFalse();
        assertThat(sm.currentState()).isEqualTo(OrderState.PENDING);
    }
}
