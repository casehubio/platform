package io.casehub.platform.agent.gemini;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;
import com.google.genai.types.Type;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class LangChain4jGeminiMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private LangChain4jGeminiMapper() {}

    static Content extractSystemInstruction(List<ChatMessage> messages) {
        List<Part> parts = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof SystemMessage sm) {
                parts.add(Part.fromText(sm.text()));
            }
        }
        if (parts.isEmpty()) return null;
        return Content.builder().parts(parts).role("user").build();
    }

    static List<Content> toGeminiContents(List<ChatMessage> messages) {
        List<Content> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            switch (msg) {
                case SystemMessage ignored -> {}
                case UserMessage um -> result.add(Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText(um.singleText())))
                        .build());
                case AiMessage ai -> {
                    List<Part> parts = new ArrayList<>();
                    if (ai.text() != null && !ai.text().isEmpty()) {
                        parts.add(Part.fromText(ai.text()));
                    }
                    if (ai.hasToolExecutionRequests()) {
                        for (var req : ai.toolExecutionRequests()) {
                            Map<String, Object> args = parseJsonToMap(req.arguments());
                            parts.add(Part.builder()
                                    .functionCall(FunctionCall.builder()
                                            .name(req.name())
                                            .args(args)
                                            .build())
                                    .build());
                        }
                    }
                    result.add(Content.builder().role("model").parts(parts).build());
                }
                case ToolExecutionResultMessage tr -> {
                    Map<String, Object> responseMap = parseJsonToMap(tr.text());
                    result.add(Content.builder()
                            .role("user")
                            .parts(List.of(Part.fromFunctionResponse(tr.toolName(), responseMap)))
                            .build());
                }
                default -> throw new IllegalArgumentException(
                        "Unsupported message type: " + msg.getClass().getSimpleName());
            }
        }
        return result;
    }

    static List<Tool> toGeminiTools(List<ToolSpecification> tools) {
        if (tools == null || tools.isEmpty()) return List.of();
        List<FunctionDeclaration> declarations = new ArrayList<>();
        for (ToolSpecification tool : tools) {
            var builder = FunctionDeclaration.builder()
                    .name(tool.name())
                    .description(tool.description() != null ? tool.description() : "");
            if (tool.parameters() != null) {
                Map<String, Object> schemaMap = dev.langchain4j.internal.JsonSchemaElementJsonUtils.toMap(tool.parameters());
                builder.parametersJsonSchema(schemaMap);
            }
            declarations.add(builder.build());
        }
        return List.of(Tool.builder().functionDeclarations(declarations).build());
    }

    static ChatResponse toChatResponse(GenerateContentResponse response, String modelName) {
        StringBuilder text = new StringBuilder();
        List<ToolExecutionRequest> toolRequests = new ArrayList<>();

        if (response.candidates().isPresent()) {
            List<Candidate> candidates = response.candidates().get();
            if (!candidates.isEmpty()) {
                Candidate candidate = candidates.get(0);
                if (candidate.content().isPresent() && candidate.content().get().parts().isPresent()) {
                    for (Part part : candidate.content().get().parts().get()) {
                        part.text().ifPresent(text::append);
                        part.functionCall().ifPresent(fc -> {
                            String argsJson = "{}";
                            if (fc.args().isPresent()) {
                                try {
                                    argsJson = OBJECT_MAPPER.writeValueAsString(fc.args().get());
                                } catch (Exception e) {
                                    argsJson = "{}";
                                }
                            }
                            toolRequests.add(ToolExecutionRequest.builder()
                                    .id(fc.id().orElse(fc.name().orElse("unknown")))
                                    .name(fc.name().orElse("unknown"))
                                    .arguments(argsJson)
                                    .build());
                        });
                    }
                }
            }
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

        FinishReason finishReason = mapFinishReason(response.finishReason());

        TokenUsage tokenUsage = null;
        if (response.usageMetadata().isPresent()) {
            GenerateContentResponseUsageMetadata usage = response.usageMetadata().get();
            int promptTokens = usage.promptTokenCount().orElse(0);
            int completionTokens = usage.candidatesTokenCount().orElse(0);
            int totalTokens = usage.totalTokenCount().orElse(promptTokens + completionTokens);
            tokenUsage = new TokenUsage(promptTokens, completionTokens, totalTokens);
        }

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder()
                        .modelName(modelName)
                        .finishReason(finishReason)
                        .tokenUsage(tokenUsage)
                        .build())
                .build();
    }

    private static FinishReason mapFinishReason(com.google.genai.types.FinishReason reason) {
        if (reason == null) return FinishReason.OTHER;
        var known = reason.knownEnum();
        if (known == null) return FinishReason.OTHER;
        return switch (known) {
            case STOP -> FinishReason.STOP;
            case MAX_TOKENS -> FinishReason.LENGTH;
            case SAFETY, RECITATION, BLOCKLIST, PROHIBITED_CONTENT, SPII,
                 IMAGE_SAFETY, IMAGE_PROHIBITED_CONTENT -> FinishReason.CONTENT_FILTER;
            case MALFORMED_FUNCTION_CALL, UNEXPECTED_TOOL_CALL,
                 TOO_MANY_TOOL_CALLS -> FinishReason.TOOL_EXECUTION;
            default -> FinishReason.OTHER;
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return OBJECT_MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
