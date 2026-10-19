package io.casehub.platform.agent.react;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.casehub.platform.agent.claude.AnthropicNativeChatModel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolUseLoopAnthropicTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void toolUseLoopWithAnthropicNativeChatModel() {
        var client = mockClientReturningSequence(TOOL_USE_RESPONSE, TEXT_RESPONSE);
        var chatModel = new AnthropicNativeChatModel(client, "claude-sonnet-4-20250514", Duration.ofSeconds(30));

        var toolSpec = ToolSpecification.builder()
                .name("get_weather")
                .description("Get current weather for a city")
                .build();

        var tool = new Tool(toolSpec, (name, args) -> "{\"temperature\": 22, \"unit\": \"celsius\"}");

        var config = new ToolUseConfig(chatModel, "You are a helpful weather assistant.", List.of(tool));
        var loop = new ToolUseLoop();
        var result = loop.run(config, "What's the weather in London?");

        assertThat(result.reason()).isEqualTo(TerminationReason.COMPLETED);
        assertThat(result.text()).isEqualTo("The weather in London is 22°C.");
        assertThat(result.cycleCount()).isEqualTo(1);
        assertThat(result.toolCalls()).hasSize(1);

        var call = result.toolCalls().getFirst();
        assertThat(call.name()).isEqualTo("get_weather");
        assertThat(call.error()).isFalse();
        assertThat(call.output()).contains("22");

        assertThat(result.inputTokens()).isGreaterThan(0);
        assertThat(result.outputTokens()).isGreaterThan(0);
    }

    @Test
    void multiCycleToolUseWithAnthropicBackend() {
        var client = mockClientReturningSequence(TOOL_USE_RESPONSE, SECOND_TOOL_USE_RESPONSE, TEXT_RESPONSE);
        var chatModel = new AnthropicNativeChatModel(client, "claude-sonnet-4-20250514", Duration.ofSeconds(30));

        var weatherTool = new Tool(
                ToolSpecification.builder().name("get_weather").description("Get weather").build(),
                (name, args) -> "{\"temperature\": 22}");

        var unitTool = new Tool(
                ToolSpecification.builder().name("convert_unit").description("Convert temperature unit").build(),
                (name, args) -> "{\"temperature\": 71.6, \"unit\": \"fahrenheit\"}");

        var config = new ToolUseConfig(chatModel, "You are helpful.", List.of(weatherTool, unitTool));
        var loop = new ToolUseLoop();
        var result = loop.run(config, "What's the weather in London in Fahrenheit?");

        assertThat(result.reason()).isEqualTo(TerminationReason.COMPLETED);
        assertThat(result.cycleCount()).isEqualTo(2);
        assertThat(result.toolCalls()).hasSize(2);
        assertThat(result.toolCalls().get(0).name()).isEqualTo("get_weather");
        assertThat(result.toolCalls().get(1).name()).isEqualTo("convert_unit");
    }

    private static AnthropicClient mockClientReturningSequence(String... jsonResponses) {
        var client = mock(AnthropicClient.class);
        var messageService = mock(com.anthropic.services.blocking.MessageService.class);
        when(client.messages()).thenReturn(messageService);

        try {
            if (jsonResponses.length == 1) {
                Message message = MAPPER.readValue(jsonResponses[0], Message.class);
                when(messageService.create(any(MessageCreateParams.class))).thenReturn(message);
            } else {
                Message first = MAPPER.readValue(jsonResponses[0], Message.class);
                Message[] rest = new Message[jsonResponses.length - 1];
                for (int i = 1; i < jsonResponses.length; i++) {
                    rest[i - 1] = MAPPER.readValue(jsonResponses[i], Message.class);
                }
                when(messageService.create(any(MessageCreateParams.class)))
                        .thenReturn(first, rest);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return client;
    }

    private static final String TOOL_USE_RESPONSE = """
            {
              "id": "msg_test_1",
              "type": "message",
              "role": "assistant",
              "model": "claude-sonnet-4-20250514",
              "content": [
                {"type": "tool_use", "id": "toolu_01", "name": "get_weather", "input": {"city": "London"}}
              ],
              "stop_reason": "tool_use",
              "stop_sequence": null,
              "usage": {"input_tokens": 50, "output_tokens": 30}
            }
            """;

    private static final String SECOND_TOOL_USE_RESPONSE = """
            {
              "id": "msg_test_2",
              "type": "message",
              "role": "assistant",
              "model": "claude-sonnet-4-20250514",
              "content": [
                {"type": "tool_use", "id": "toolu_02", "name": "convert_unit", "input": {"value": 22, "from": "celsius", "to": "fahrenheit"}}
              ],
              "stop_reason": "tool_use",
              "stop_sequence": null,
              "usage": {"input_tokens": 80, "output_tokens": 25}
            }
            """;

    private static final String TEXT_RESPONSE = """
            {
              "id": "msg_test_3",
              "type": "message",
              "role": "assistant",
              "model": "claude-sonnet-4-20250514",
              "content": [{"type": "text", "text": "The weather in London is 22\\u00b0C."}],
              "stop_reason": "end_turn",
              "stop_sequence": null,
              "usage": {"input_tokens": 100, "output_tokens": 15}
            }
            """;
}
