package io.casehub.platform.agent.react;

@FunctionalInterface
public interface ToolUseListener {
    void onCycle(ToolUseCycleEvent event);

    static ToolUseListener noOp() {
        return event -> {};
    }
}
