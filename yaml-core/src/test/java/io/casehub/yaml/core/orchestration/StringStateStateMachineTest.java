package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StringStateStateMachineTest {

    @Test
    void stringStates_transitionSucceeds() {
        String PENDING = "PENDING".intern();
        String APPROVED = "APPROVED".intern();
        String REJECTED = "REJECTED".intern();

        var sm = DefaultOrcStateMachine.<String>builder("order", PENDING)
                .transition(PENDING, APPROVED)
                .transition(PENDING, REJECTED)
                .terminal(APPROVED, REJECTED)
                .build();

        assertThat(sm.currentState()).isEqualTo(PENDING);
        assertThat(sm.transition(PENDING, APPROVED)).isTrue();
        assertThat(sm.currentState()).isEqualTo(APPROVED);
    }

    @Test
    void stringStates_invalidTransitionThrows() {
        String A = "A".intern();
        String B = "B".intern();
        String C = "C".intern();

        var sm = DefaultOrcStateMachine.<String>builder("test", A)
                .transition(A, B)
                .terminal(B, C)
                .build();

        assertThatThrownBy(() -> sm.transition(A, C))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void stringStates_onEnterHandlerFires() {
        String IDLE = "IDLE".intern();
        String RUNNING = "RUNNING".intern();

        var sm = DefaultOrcStateMachine.<String>builder("lifecycle", IDLE)
                .transition(IDLE, RUNNING)
                .terminal(RUNNING)
                .build();

        var entered = new AtomicBoolean(false);
        sm.onEnter(RUNNING, () -> entered.set(true));
        sm.transition(IDLE, RUNNING);
        assertThat(entered.get()).isTrue();
    }

    @Test
    void stringStates_terminalStateRejectsTransition() {
        String A = "A".intern();
        String B = "B".intern();

        var sm = DefaultOrcStateMachine.<String>builder("test", A)
                .transition(A, B)
                .terminal(B)
                .build();

        sm.transition(A, B);

        assertThatThrownBy(() -> sm.transition(B, A))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    void stringStates_eventRouterFires() {
        String PENDING = "PENDING".intern();
        String APPROVED = "APPROVED".intern();

        var builder = DefaultOrcStateMachine.<String>builder("order", PENDING)
                .on("approve", PENDING, APPROVED)
                .terminal(APPROVED);

        var sm = builder.build();
        var router = builder.buildRouter(sm);

        assertThat(router.fire("approve")).isTrue();
        assertThat(sm.currentState()).isEqualTo(APPROVED);
    }

    @Test
    void stringStates_scenarioScope_createsStateMachine() {
        var scope = new DefaultScenarioScope();
        String IDLE = "IDLE".intern();
        var sm = scope.stateMachine("test", String.class, IDLE);
        assertThat(sm).isNotNull();
        assertThat(sm.currentState()).isEqualTo(IDLE);
    }
}
