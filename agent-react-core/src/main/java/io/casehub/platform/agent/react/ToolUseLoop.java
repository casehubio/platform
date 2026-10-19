package io.casehub.platform.agent.react;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ToolUseLoop {

    private static final Logger LOG = Logger.getLogger(ToolUseLoop.class);

    public ToolUseResult run(ToolUseConfig config, String userMessage) {
        var start = Instant.now();

        var toolsByName = config.tools().stream()
                .collect(Collectors.toMap(t -> t.spec().name(), t -> t));
        var toolSpecs = config.tools().stream()
                .map(Tool::spec)
                .toList();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(config.systemPrompt()));
        messages.add(UserMessage.from(userMessage));

        long totalInputTokens = 0;
        long totalOutputTokens = 0;
        List<ToolCallRecord> allToolCalls = new ArrayList<>();
        int cycleCount = 0;

        while (cycleCount < config.maxCycles()) {
            if (Thread.interrupted()) {
                return buildResult(null, totalInputTokens, totalOutputTokens,
                        cycleCount, allToolCalls, start, TerminationReason.INTERRUPTED);
            }

            var request = ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(toolSpecs)
                    .build();

            ChatResponse response = config.model().chat(request);
            AiMessage aiMessage = response.aiMessage();
            messages.add(aiMessage);

            TokenUsage usage = response.metadata().tokenUsage();
            if (usage != null) {
                totalInputTokens += usage.inputTokenCount() != null ? usage.inputTokenCount() : 0;
                totalOutputTokens += usage.outputTokenCount() != null ? usage.outputTokenCount() : 0;
            }

            if (!aiMessage.hasToolExecutionRequests()) {
                return buildResult(aiMessage.text(), totalInputTokens, totalOutputTokens,
                        cycleCount, allToolCalls, start, TerminationReason.COMPLETED);
            }

            List<ToolCallRecord> cycleToolCalls = new ArrayList<>();
            for (var toolRequest : aiMessage.toolExecutionRequests()) {
                var callStart = Instant.now();
                String name = toolRequest.name();
                String arguments = toolRequest.arguments();
                String output;
                boolean error = false;

                Tool tool = toolsByName.get(name);
                if (tool == null) {
                    output = "Error: unknown tool '" + name + "'";
                    error = true;
                } else {
                    try {
                        output = tool.executor().execute(name, arguments);
                    } catch (Exception e) {
                        output = "Error: " + e.getMessage();
                        error = true;
                    }
                }

                var record = new ToolCallRecord(name, arguments, output, error,
                        Duration.between(callStart, Instant.now()));
                cycleToolCalls.add(record);
                allToolCalls.add(record);

                messages.add(ToolExecutionResultMessage.from(toolRequest, output));
            }

            var cycleEvent = new ToolUseCycleEvent(
                    cycleCount, aiMessage.text(), cycleToolCalls,
                    usage != null && usage.inputTokenCount() != null ? usage.inputTokenCount() : 0,
                    usage != null && usage.outputTokenCount() != null ? usage.outputTokenCount() : 0);
            try {
                config.listener().onCycle(cycleEvent);
            } catch (Exception e) {
                LOG.warnf(e, "ToolUseListener threw on cycle %d — swallowed", cycleCount);
            }

            cycleCount++;
        }

        return buildResult(null, totalInputTokens, totalOutputTokens,
                cycleCount, allToolCalls, start, TerminationReason.MAX_CYCLES_REACHED);
    }

    private ToolUseResult buildResult(String text, long inputTokens, long outputTokens,
                                      int cycleCount, List<ToolCallRecord> toolCalls,
                                      Instant start, TerminationReason reason) {
        return new ToolUseResult(text, inputTokens, outputTokens,
                cycleCount, toolCalls, Duration.between(start, Instant.now()), reason);
    }
}
