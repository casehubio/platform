package io.casehub.platform.agent.claude;

import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
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
import java.util.Map;

final class LangChain4jAnthropicMapper {

    private LangChain4jAnthropicMapper() {}

    static List<TextBlockParam> extractSystemBlocks(List<ChatMessage> messages) {
        List<TextBlockParam> blocks = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof SystemMessage sm) {
                blocks.add(TextBlockParam.builder()
                        .text(sm.text())
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build());
            }
        }
        return blocks;
    }

    static List<MessageParam> toAnthropicMessages(List<ChatMessage> messages) {
        List<MessageParam> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            switch (msg) {
                case SystemMessage ignored -> {} // handled by extractSystemBlocks
                case UserMessage um -> result.add(MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .content(um.singleText())
                        .build());
                case AiMessage ai -> {
                    if (ai.hasToolExecutionRequests()) {
                        var contentBlocks = ai.toolExecutionRequests().stream()
                                .map(req -> {
                                    var inputMap = parseJsonToMap(req.arguments());
                                    return com.anthropic.models.messages.ContentBlockParam.ofToolUse(
                                            ToolUseBlockParam.builder()
                                                    .id(req.id())
                                                    .name(req.name())
                                                    .input(com.anthropic.core.JsonValue.from(inputMap))
                                                    .build());
                                })
                                .toList();
                        result.add(MessageParam.builder()
                                .role(MessageParam.Role.ASSISTANT)
                                .contentOfBlockParams(contentBlocks)
                                .build());
                    } else {
                        result.add(MessageParam.builder()
                                .role(MessageParam.Role.ASSISTANT)
                                .content(ai.text() != null ? ai.text() : "")
                                .build());
                    }
                }
                case ToolExecutionResultMessage tr -> result.add(MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .contentOfBlockParams(List.of(
                                com.anthropic.models.messages.ContentBlockParam.ofToolResult(
                                        ToolResultBlockParam.builder()
                                                .toolUseId(tr.id())
                                                .content(tr.text())
                                                .build())))
                        .build());
                default -> throw new IllegalArgumentException(
                        "Unsupported message type: " + msg.getClass().getSimpleName());
            }
        }
        return result;
    }

    static List<Tool> toAnthropicTools(List<ToolSpecification> tools) {
        if (tools == null || tools.isEmpty()) return List.of();
        List<Tool> result = new ArrayList<>();
        for (int i = 0; i < tools.size(); i++) {
            ToolSpecification tool = tools.get(i);
            Map<String, Object> schemaMap;
            if (tool.parameters() != null) {
                schemaMap = dev.langchain4j.internal.JsonSchemaElementJsonUtils.toMap(tool.parameters());
            } else {
                schemaMap = Map.of("type", "object");
            }
            var builder = Tool.builder()
                    .name(tool.name())
                    .description(tool.description() != null ? tool.description() : "")
                    .inputSchema(Tool.InputSchema.builder()
                            .properties(com.anthropic.core.JsonValue.from(
                                    schemaMap.getOrDefault("properties", Map.of())))
                            .build());
            // cache_control on the last tool for prompt caching
            if (i == tools.size() - 1) {
                builder.cacheControl(CacheControlEphemeral.builder().build());
            }
            result.add(builder.build());
        }
        return result;
    }

    static ChatResponse toChatResponse(Message message) {
        StringBuilder text = new StringBuilder();
        List<ToolExecutionRequest> toolRequests = new ArrayList<>();

        for (ContentBlock block : message.content()) {
            block.text().ifPresent(tb -> text.append(tb.text()));
            block.toolUse().ifPresent(tu -> toolRequests.add(
                    ToolExecutionRequest.builder()
                            .id(tu.id())
                            .name(tu.name())
                            .arguments(tu._input().toString())
                            .build()));
        }

        AiMessage aiMessage;
        if (!toolRequests.isEmpty()) {
            String textContent = text.length() > 0 ? text.toString() : null;
            aiMessage = textContent != null
                    ? new AiMessage(textContent, toolRequests)
                    : AiMessage.from(toolRequests);
        } else {
            aiMessage = AiMessage.from(text.toString());
        }

        FinishReason finishReason = message.stopReason()
                .map(sr -> switch (sr.value()) {
                    case END_TURN -> FinishReason.STOP;
                    case TOOL_USE -> FinishReason.TOOL_EXECUTION;
                    case MAX_TOKENS -> FinishReason.LENGTH;
                    default -> FinishReason.OTHER;
                })
                .orElse(FinishReason.OTHER);

        TokenUsage tokenUsage = new TokenUsage(
                (int) message.usage().inputTokens(),
                (int) message.usage().outputTokens());

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder()
                        .modelName(message.model().asString())
                        .finishReason(finishReason)
                        .tokenUsage(tokenUsage)
                        .build())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
