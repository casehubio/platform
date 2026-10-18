package io.casehub.platform.agent.openai;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiNativeChatModelTest {

    @Test
    void chatReturnsTextResponse() {
        var client = mockClientReturning("Hello world", ChatCompletion.Choice.FinishReason.STOP);
        var model = new OpenAiNativeChatModel(client, "gpt-4o", Duration.ofSeconds(30));

        var request = ChatRequest.builder()
                .messages(SystemMessage.from("You are helpful"), UserMessage.from("Say hello"))
                .build();

        ChatResponse response = model.chat(request);

        assertThat(response.aiMessage().text()).isEqualTo("Hello world");
        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.metadata().modelName()).isEqualTo("gpt-4o");
    }

    @Test
    void chatWithToolsReturnsToolExecutionRequest() {
        var client = mockClientReturningToolCall("get_weather", "{\"location\":\"London\"}");
        var model = new OpenAiNativeChatModel(client, "gpt-4o", Duration.ofSeconds(30));

        var request = ChatRequest.builder()
                .messages(UserMessage.from("What's the weather in London?"))
                .toolSpecifications(ToolSpecification.builder()
                        .name("get_weather")
                        .description("Get weather for a location")
                        .build())
                .build();

        ChatResponse response = model.chat(request);

        assertThat(response.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(response.aiMessage().toolExecutionRequests()).hasSize(1);
        var toolReq = response.aiMessage().toolExecutionRequests().get(0);
        assertThat(toolReq.name()).isEqualTo("get_weather");
        assertThat(toolReq.arguments()).isEqualTo("{\"location\":\"London\"}");
        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
    }

    @Test
    void chatPassesToolResultMessages() {
        var captor = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        var client = mockClientReturning("The weather is sunny", ChatCompletion.Choice.FinishReason.STOP);
        var completionService = client.chat().completions();
        var model = new OpenAiNativeChatModel(client, "gpt-4o", Duration.ofSeconds(30));

        var request = ChatRequest.builder()
                .messages(
                        UserMessage.from("Weather?"),
                        AiMessage.from(List.of(dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                                .id("call-1").name("get_weather").arguments("{}").build())),
                        ToolExecutionResultMessage.from("call-1", "get_weather", "Sunny, 22C"))
                .build();

        model.chat(request);

        verify(completionService).create(captor.capture());
        var params = captor.getValue();
        assertThat(params.messages()).hasSize(3);
    }

    @Test
    void honorsPerRequestModelName() {
        var captor = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        var client = mockClientReturning("response", ChatCompletion.Choice.FinishReason.STOP);
        var completionService = client.chat().completions();
        var model = new OpenAiNativeChatModel(client, "gpt-4o", Duration.ofSeconds(30));

        var request = ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .modelName("gpt-4o-mini")
                .build();

        model.chat(request);

        verify(completionService).create(captor.capture());
        assertThat(captor.getValue().model().toString()).contains("gpt-4o-mini");
    }

    private static OpenAIClient mockClientReturning(String text,
                                                      ChatCompletion.Choice.FinishReason finishReason) {
        var client = mock(OpenAIClient.class);
        var chatService = mock(com.openai.services.blocking.ChatService.class);
        var completionService = mock(com.openai.services.blocking.chat.ChatCompletionService.class);

        when(client.chat()).thenReturn(chatService);
        when(chatService.completions()).thenReturn(completionService);

        var json = """
                {"id":"test-id","object":"chat.completion","model":"gpt-4o","choices":[{"index":0,"message":{"role":"assistant","content":"%s","refusal":null},"logprobs":null,"finish_reason":"%s"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                """.formatted(text, finishReason._value().asString().orElse("stop"));
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        ChatCompletion completion;
        try {
            completion = objectMapper.readValue(json, ChatCompletion.class);
        } catch (Exception e) { throw new RuntimeException(e); }

        when(completionService.create(any(ChatCompletionCreateParams.class)))
                .thenReturn(completion);
        return client;
    }

    private static OpenAIClient mockClientReturningToolCall(String toolName, String arguments) {
        var client = mock(OpenAIClient.class);
        var chatService = mock(com.openai.services.blocking.ChatService.class);
        var completionService = mock(com.openai.services.blocking.chat.ChatCompletionService.class);

        when(client.chat()).thenReturn(chatService);
        when(chatService.completions()).thenReturn(completionService);

        var json = """
                {"id":"test-id","object":"chat.completion","model":"gpt-4o","choices":[{"index":0,"message":{"role":"assistant","content":null,"refusal":null,"tool_calls":[{"id":"call-1","type":"function","function":{"name":"%s","arguments":"%s"}}]},"logprobs":null,"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                """.formatted(toolName, arguments.replace("\"", "\\\""));
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        ChatCompletion completion;
        try {
            completion = objectMapper.readValue(json, ChatCompletion.class);
        } catch (Exception e) { throw new RuntimeException(e); }

        when(completionService.create(any(ChatCompletionCreateParams.class)))
                .thenReturn(completion);
        return client;
    }
}
