package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventRouterTest {

    enum OrderState { IDLE, PENDING, APPROVED, CANCELLED }

    private DefaultOrcStateMachine.Builder<OrderState> builder;

    @BeforeEach
    void setUp() {
        builder = DefaultOrcStateMachine.builder("order", OrderState.class, OrderState.IDLE)
                .transition(OrderState.IDLE, OrderState.PENDING)
                .on("approve", OrderState.PENDING, OrderState.APPROVED)
                .on("cancel", OrderState.PENDING, OrderState.CANCELLED)
                .terminal(OrderState.APPROVED, OrderState.CANCELLED);
    }

    @Test
    void fire_matchesEventAndState() {
        var sm = builder.build();
        var router = builder.buildRouter(sm);
        sm.transition(OrderState.IDLE, OrderState.PENDING);
        assertThat(router.fire("approve")).isTrue();
        assertThat(sm.currentState()).isEqualTo(OrderState.APPROVED);
    }

    @Test
    void fire_wrongState_returnsFalse() {
        var sm = builder.build();
        var router = builder.buildRouter(sm);
        assertThat(router.fire("approve")).isFalse();
    }

    @Test
    void fire_unknownEvent_returnsFalse() {
        var sm = builder.build();
        var router = builder.buildRouter(sm);
        sm.transition(OrderState.IDLE, OrderState.PENDING);
        assertThat(router.fire("unknown")).isFalse();
    }

    @Test
    void fire_withGuard_guardTrue_transitions() {
        var guardedBuilder = DefaultOrcStateMachine.builder("guarded", OrderState.class, OrderState.IDLE)
                .transition(OrderState.IDLE, OrderState.PENDING)
                .on("approve", OrderState.PENDING, OrderState.APPROVED, ctx -> ctx != null)
                .terminal(OrderState.APPROVED);
        var sm = guardedBuilder.build();
        var router = guardedBuilder.buildRouter(sm);
        sm.transition(OrderState.IDLE, OrderState.PENDING);
        assertThat(router.fire("approve", "context")).isTrue();
        assertThat(sm.currentState()).isEqualTo(OrderState.APPROVED);
    }

    @Test
    void fire_withGuard_guardFalse_returnsFalse() {
        var guardedBuilder = DefaultOrcStateMachine.builder("guarded", OrderState.class, OrderState.IDLE)
                .transition(OrderState.IDLE, OrderState.PENDING)
                .on("approve", OrderState.PENDING, OrderState.APPROVED, ctx -> false)
                .terminal(OrderState.APPROVED);
        var sm = guardedBuilder.build();
        var router = guardedBuilder.buildRouter(sm);
        sm.transition(OrderState.IDLE, OrderState.PENDING);
        assertThat(router.fire("approve", "ctx")).isFalse();
        assertThat(sm.currentState()).isEqualTo(OrderState.PENDING);
    }

    @Test
    void targeting_retargetsToBlockingWrapper() {
        var sm = builder.build();
        var blocking = new DefaultBlockingOrcStateMachine<>(sm);
        var router = builder.buildRouter(blocking);
        blocking.transition(OrderState.IDLE, OrderState.PENDING);
        assertThat(router.fire("approve")).isTrue();
        assertThat(blocking.currentState()).isEqualTo(OrderState.APPROVED);
    }

    @Test
    void fire_cancel_matchesEventAndState() {
        var sm = builder.build();
        var router = builder.buildRouter(sm);
        sm.transition(OrderState.IDLE, OrderState.PENDING);
        assertThat(router.fire("cancel")).isTrue();
        assertThat(sm.currentState()).isEqualTo(OrderState.CANCELLED);
    }
}
