package io.casehub.platform.agent.react;

import dev.langchain4j.model.chat.ChatModel;

import java.util.List;
import java.util.Objects;

public record ToolUseConfig(
        ChatModel model,
        String systemPrompt,
        List<Tool> tools,
        int maxCycles,
        ToolUseListener listener) {

    private static final int DEFAULT_MAX_CYCLES = 10;

    public ToolUseConfig {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        tools = List.copyOf(tools);
        if (maxCycles <= 0) {
            throw new IllegalArgumentException("maxCycles must be positive");
        }
        if (listener == null) {
            listener = ToolUseListener.noOp();
        }
    }

    public ToolUseConfig(ChatModel model, String systemPrompt, List<Tool> tools) {
        this(model, systemPrompt, tools, DEFAULT_MAX_CYCLES, ToolUseListener.noOp());
    }

    public ToolUseConfig(ChatModel model, String systemPrompt, List<Tool> tools, int maxCycles) {
        this(model, systemPrompt, tools, maxCycles, ToolUseListener.noOp());
    }
}
