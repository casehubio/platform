package io.casehub.yaml.core.orchestration;

public interface OrcStateMachine<S extends Enum<S>> extends OrcPrimitive {
    S currentState();
    boolean transition(S from, S to);
    boolean transition(S from, S to, Object payload);
    void onTransition(S from, S to, TransitionHandler handler);
    void onEnter(S state, StateHandler handler);
    void onExit(S state, StateHandler handler);
}
