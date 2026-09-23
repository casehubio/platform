package io.casehub.yaml.core.orchestration;

@FunctionalInterface
public interface TransitionHandler {
    void onTransition(Object payload);
}
