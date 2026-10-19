package io.casehub.platform.agent.react;

@FunctionalInterface
public interface ToolExecutor {
    String execute(String name, String argumentsJson);
}
