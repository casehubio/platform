package io.casehub.platform.agent.openai;

import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.FunctionDefinition;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;

final class LangChain4jOpenAiMapper {

    private LangChain4jOpenAiMapper() {}

    static List<ChatCompletionMessageParam> toOpenAiMessages(List<ChatMessage> messages) {
        List<ChatCompletionMessageParam> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            switch (msg) {
                case SystemMessage sm -> result.add(ChatCompletionMessageParam.ofSystem(
                        ChatCompletionSystemMessageParam.builder()
                                .content(sm.text()).build()));
                case UserMessage um -> result.add(ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder()
                                .content(um.singleText()).build()));
                case AiMessage ai -> {
                    var builder = ChatCompletionAssistantMessageParam.builder();
                    if (ai.text() != null) {
                        builder.content(ai.text());
                    }
                    if (ai.hasToolExecutionRequests()) {
                        var toolCalls = ai.toolExecutionRequests().stream()
                                .map(req -> ChatCompletionMessageToolCall.ofFunction(
                                        ChatCompletionMessageFunctionToolCall.builder()
                                                .id(req.id())
                                                .function(ChatCompletionMessageFunctionToolCall.Function.builder()
                                                        .name(req.name())
                                                        .arguments(req.arguments())
                                                        .build())
                                                .build()))
                                .toList();
                        builder.toolCalls(toolCalls);
                    }
                    result.add(ChatCompletionMessageParam.ofAssistant(builder.build()));
                }
                case ToolExecutionResultMessage tr -> result.add(ChatCompletionMessageParam.ofTool(
                        ChatCompletionToolMessageParam.builder()
                                .toolCallId(tr.id())
                                .content(tr.text())
                                .build()));
                default -> throw new IllegalArgumentException(
                        "Unsupported message type: " + msg.getClass().getSimpleName());
            }
        }
        return result;
    }

    static List<ChatCompletionTool> toOpenAiTools(List<ToolSpecification> tools) {
        if (tools == null || tools.isEmpty()) return List.of();
        return tools.stream().map(tool -> {
            java.util.Map<String, Object> schemaMap;
            if (tool.parameters() != null) {
                schemaMap = dev.langchain4j.internal.JsonSchemaElementJsonUtils.toMap(tool.parameters());
            } else {
                schemaMap = java.util.Map.of("type", "object");
            }
            return ChatCompletionTool.ofFunction(
                    ChatCompletionFunctionTool.builder()
                            .function(FunctionDefinition.builder()
                                    .name(tool.name())
                                    .description(tool.description() != null ? tool.description() : "")
                                    .parameters(com.openai.core.JsonValue.from(schemaMap))
                                    .build())
                            .build());
        }).toList();
    }

    static ChatResponse toChatResponse(ChatCompletion completion) {
        ChatCompletion.Choice choice = completion.choices().get(0);
        ChatCompletionMessage msg = choice.message();

        String text = msg.content().orElse(null);
        List<ToolExecutionRequest> toolRequests = new ArrayList<>();
        msg.toolCalls().ifPresent(toolCalls -> {
            for (var tc : toolCalls) {
                tc.function().ifPresent(fn -> toolRequests.add(ToolExecutionRequest.builder()
                        .id(fn.id())
                        .name(fn.function().name())
                        .arguments(fn.function().arguments())
                        .build()));
            }
        });

        AiMessage aiMessage;
        if (!toolRequests.isEmpty()) {
            aiMessage = text != null
                    ? new AiMessage(text, toolRequests)
                    : AiMessage.from(toolRequests);
        } else {
            aiMessage = AiMessage.from(text != null ? text : "");
        }

        FinishReason finishReason = switch (choice.finishReason().value()) {
            case STOP -> FinishReason.STOP;
            case TOOL_CALLS -> FinishReason.TOOL_EXECUTION;
            case LENGTH -> FinishReason.LENGTH;
            case CONTENT_FILTER -> FinishReason.CONTENT_FILTER;
            default -> FinishReason.OTHER;
        };

        TokenUsage tokenUsage = null;
        if (completion.usage().isPresent()) {
            var usage = completion.usage().get();
            tokenUsage = new TokenUsage(
                    (int) usage.promptTokens(),
                    (int) usage.completionTokens(),
                    (int) usage.totalTokens());
        }

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder()
                        .modelName(completion.model())
                        .finishReason(finishReason)
                        .tokenUsage(tokenUsage)
                        .build())
                .build();
    }
}
