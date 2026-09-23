package io.casehub.yaml.core.orchestration;

@FunctionalInterface
public interface StateHandler {
    void onState();
}
