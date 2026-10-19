package io.casehub.platform.agent.gemini;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LangChain4jGeminiMapperTest {

    @Test
    void extractSystemInstructionReturnsNullWhenNoSystemMessages() {
        List<ChatMessage> messages = List.of(UserMessage.from("hello"));
        assertThat(LangChain4jGeminiMapper.extractSystemInstruction(messages)).isNull();
    }

    @Test
    void extractSystemInstructionCombinesMultipleSystemMessages() {
        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are helpful"),
                UserMessage.from("hello"),
                SystemMessage.from("Be concise"));
        Content system = LangChain4jGeminiMapper.extractSystemInstruction(messages);
        assertThat(system).isNotNull();
        assertThat(system.parts().orElseThrow()).hasSize(2);
    }

    @Test
    void toGeminiContentsSkipsSystemMessages() {
        List<ChatMessage> messages = List.of(
                SystemMessage.from("system"),
                UserMessage.from("hello"));
        var contents = LangChain4jGeminiMapper.toGeminiContents(messages);
        assertThat(contents).hasSize(1);
        assertThat(contents.get(0).role().orElseThrow()).isEqualTo("user");
    }

    @Test
    void toGeminiContentsMapsUserMessage() {
        List<ChatMessage> msgs = List.of(UserMessage.from("hello"));
        var contents = LangChain4jGeminiMapper.toGeminiContents(msgs);
        assertThat(contents).hasSize(1);
        assertThat(contents.get(0).role().orElseThrow()).isEqualTo("user");
        assertThat(contents.get(0).text()).isEqualTo("hello");
    }

    @Test
    void toGeminiContentsMapsAiMessageWithText() {
        List<ChatMessage> msgs = List.of(AiMessage.from("response"));
        var contents = LangChain4jGeminiMapper.toGeminiContents(msgs);
        assertThat(contents).hasSize(1);
        assertThat(contents.get(0).role().orElseThrow()).isEqualTo("model");
        assertThat(contents.get(0).text()).isEqualTo("response");
    }

    @Test
    void toGeminiContentsMapsAiMessageWithToolCalls() {
        var aiMsg = new AiMessage("thinking", List.of(
                ToolExecutionRequest.builder()
                        .id("call_1")
                        .name("get_weather")
                        .arguments("{\"location\":\"London\"}")
                        .build()));
        List<ChatMessage> msgs = List.of(aiMsg);
        var contents = LangChain4jGeminiMapper.toGeminiContents(msgs);
        assertThat(contents).hasSize(1);
        var parts = contents.get(0).parts().orElseThrow();
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).text().orElseThrow()).isEqualTo("thinking");
        assertThat(parts.get(1).functionCall().orElseThrow().name().orElseThrow()).isEqualTo("get_weather");
    }

    @Test
    void toGeminiContentsMapsToolResult() {
        var toolResult = ToolExecutionResultMessage.from(
                ToolExecutionRequest.builder().id("call_1").name("get_weather").arguments("{}").build(),
                "{\"temp\": 20}");
        List<ChatMessage> msgs = List.of(toolResult);
        var contents = LangChain4jGeminiMapper.toGeminiContents(msgs);
        assertThat(contents).hasSize(1);
        assertThat(contents.get(0).role().orElseThrow()).isEqualTo("user");
    }

    @Test
    void toGeminiToolsCreatesToolWithFunctionDeclarations() {
        var tools = List.of(
                ToolSpecification.builder()
                        .name("search")
                        .description("Search the web")
                        .build(),
                ToolSpecification.builder()
                        .name("calculate")
                        .description("Do math")
                        .build());
        var geminiTools = LangChain4jGeminiMapper.toGeminiTools(tools);
        assertThat(geminiTools).hasSize(1);
        var declarations = geminiTools.get(0).functionDeclarations().orElseThrow();
        assertThat(declarations).hasSize(2);
        assertThat(declarations.get(0).name().orElseThrow()).isEqualTo("search");
        assertThat(declarations.get(1).name().orElseThrow()).isEqualTo("calculate");
    }

    @Test
    void toGeminiToolsReturnsEmptyForNullOrEmpty() {
        assertThat(LangChain4jGeminiMapper.toGeminiTools(null)).isEmpty();
        assertThat(LangChain4jGeminiMapper.toGeminiTools(List.of())).isEmpty();
    }

    @Test
    void toChatResponseMapsTextResponse() {
        var response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(List.of(Part.fromText("Hello from Gemini")))
                                .build())
                        .finishReason(new com.google.genai.types.FinishReason(
                                com.google.genai.types.FinishReason.Known.STOP))
                        .build()))
                .usageMetadata(GenerateContentResponseUsageMetadata.builder()
                        .promptTokenCount(10)
                        .candidatesTokenCount(5)
                        .totalTokenCount(15)
                        .build())
                .build();

        var chatResponse = LangChain4jGeminiMapper.toChatResponse(response, "gemini-2.5-flash");

        assertThat(chatResponse.aiMessage().text()).isEqualTo("Hello from Gemini");
        assertThat(chatResponse.metadata().finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(chatResponse.metadata().modelName()).isEqualTo("gemini-2.5-flash");
        assertThat(chatResponse.metadata().tokenUsage().inputTokenCount()).isEqualTo(10);
        assertThat(chatResponse.metadata().tokenUsage().outputTokenCount()).isEqualTo(5);
    }

    @Test
    void toChatResponseMapsToolCallResponse() {
        var response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(List.of(Part.builder()
                                        .functionCall(FunctionCall.builder()
                                                .name("get_weather")
                                                .args(Map.of("location", "London"))
                                                .build())
                                        .build()))
                                .build())
                        .finishReason(new com.google.genai.types.FinishReason(
                                com.google.genai.types.FinishReason.Known.STOP))
                        .build()))
                .build();

        var chatResponse = LangChain4jGeminiMapper.toChatResponse(response, "gemini-2.5-flash");

        assertThat(chatResponse.aiMessage().hasToolExecutionRequests()).isTrue();
        var toolReq = chatResponse.aiMessage().toolExecutionRequests().get(0);
        assertThat(toolReq.name()).isEqualTo("get_weather");
        assertThat(toolReq.arguments()).contains("London");
    }

    @Test
    void toChatResponseHandlesMaxTokensFinishReason() {
        var response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(List.of(Part.fromText("truncated")))
                                .build())
                        .finishReason(new com.google.genai.types.FinishReason(
                                com.google.genai.types.FinishReason.Known.MAX_TOKENS))
                        .build()))
                .build();

        var chatResponse = LangChain4jGeminiMapper.toChatResponse(response, "gemini-2.5-flash");
        assertThat(chatResponse.metadata().finishReason()).isEqualTo(FinishReason.LENGTH);
    }

    @Test
    void toChatResponseHandsSafetyFinishReason() {
        var response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(List.of(Part.fromText("")))
                                .build())
                        .finishReason(new com.google.genai.types.FinishReason(
                                com.google.genai.types.FinishReason.Known.SAFETY))
                        .build()))
                .build();

        var chatResponse = LangChain4jGeminiMapper.toChatResponse(response, "gemini-2.5-flash");
        assertThat(chatResponse.metadata().finishReason()).isEqualTo(FinishReason.CONTENT_FILTER);
    }
}
