package io.casehub.platform.agent.react;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolUseLoopTest {

    @Test
    void singleToolCallThenTextResponse() {
        var toolSpec = ToolSpecification.builder()
                .name("get_weather")
                .description("Get current weather")
                .build();
        var tool = new Tool(toolSpec, (name, args) -> "{\"temp\": 22}");
        var chatModel = mock(ChatModel.class);

        var toolRequest = ToolExecutionRequest.builder()
                .id("call-1")
                .name("get_weather")
                .arguments("{\"city\": \"London\"}")
                .build();
        var response1 = ChatResponse.builder()
                .aiMessage(AiMessage.from(toolRequest))
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(new TokenUsage(10, 5))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build())
                .build();

        var response2 = ChatResponse.builder()
                .aiMessage(AiMessage.from("The weather in London is 22°C."))
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(new TokenUsage(20, 10))
                        .finishReason(FinishReason.STOP)
                        .build())
                .build();

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response1)
                .thenReturn(response2);

        var config = new ToolUseConfig(chatModel, "You are helpful.", List.of(tool));
        var loop = new ToolUseLoop();
        var result = loop.run(config, "What's the weather in London?");

        assertThat(result.reason()).isEqualTo(TerminationReason.COMPLETED);
        assertThat(result.text()).isEqualTo("The weather in London is 22°C.");
        assertThat(result.cycleCount()).isEqualTo(1);
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.toolCalls().getFirst().name()).isEqualTo("get_weather");
        assertThat(result.toolCalls().getFirst().error()).isFalse();
        assertThat(result.inputTokens()).isEqualTo(30);
        assertThat(result.outputTokens()).isEqualTo(15);
    }

    @Test
    void unknownToolNameFeedsErrorBack() {
        var chatModel = mock(ChatModel.class);

        var toolRequest = ToolExecutionRequest.builder()
                .id("call-1")
                .name("nonexistent_tool")
                .arguments("{}")
                .build();
        var response1 = ChatResponse.builder()
                .aiMessage(AiMessage.from(toolRequest))
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(new TokenUsage(10, 5))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build())
                .build();

        var response2 = ChatResponse.builder()
                .aiMessage(AiMessage.from("Sorry, I made an error."))
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(new TokenUsage(15, 8))
                        .finishReason(FinishReason.STOP)
                        .build())
                .build();

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response1)
                .thenReturn(response2);

        var config = new ToolUseConfig(chatModel, "You are helpful.", List.of());
        var loop = new ToolUseLoop();
        var result = loop.run(config, "Do something");

        assertThat(result.reason()).isEqualTo(TerminationReason.COMPLETED);
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.toolCalls().getFirst().error()).isTrue();
        assertThat(result.toolCalls().getFirst().output()).contains("unknown tool");
    }

    @Test
    void executorExceptionBecomesErrorResult() {
        var toolSpec = ToolSpecification.builder()
                .name("failing_tool")
                .description("Always fails")
                .build();
        var tool = new Tool(toolSpec, (name, args) -> {
            throw new RuntimeException("Connection refused");
        });
        var chatModel = mock(ChatModel.class);

        var toolRequest = ToolExecutionRequest.builder()
                .id("call-1")
                .name("failing_tool")
                .arguments("{}")
                .build();
        var response1 = ChatResponse.builder()
                .aiMessage(AiMessage.from(toolRequest))
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(new TokenUsage(10, 5))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build())
                .build();

        var response2 = ChatResponse.builder()
                .aiMessage(AiMessage.from("Tool failed, sorry."))
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(new TokenUsage(15, 8))
                        .finishReason(FinishReason.STOP)
                        .build())
                .build();

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response1)
                .thenReturn(response2);

        var config = new ToolUseConfig(chatModel, "You are helpful.", List.of(tool));
        var loop = new ToolUseLoop();
        var result = loop.run(config, "Use the tool");

        assertThat(result.toolCalls().getFirst().error()).isTrue();
        assertThat(result.toolCalls().getFirst().output()).contains("Connection refused");
        assertThat(result.reason()).isEqualTo(TerminationReason.COMPLETED);
    }

    @Test
    void maxCyclesExceededReturnsMaxCyclesReached() {
        var toolSpec = ToolSpecification.builder()
                .name("loop_tool")
                .description("Always called")
                .build();
        var tool = new Tool(toolSpec, (name, args) -> "ok");
        var chatModel = mock(ChatModel.class);

        var toolRequest = ToolExecutionRequest.builder()
                .id("call-1")
                .name("loop_tool")
                .arguments("{}")
                .build();
        var toolResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from(toolRequest))
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(new TokenUsage(10, 5))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build())
                .build();

        when(chatModel.chat(any(ChatRequest.class))).thenReturn(toolResponse);

        var config = new ToolUseConfig(chatModel, "You are helpful.", List.of(tool), 3);
        var loop = new ToolUseLoop();
        var result = loop.run(config, "Keep going");

        assertThat(result.reason()).isEqualTo(TerminationReason.MAX_CYCLES_REACHED);
        assertThat(result.cycleCount()).isEqualTo(3);
        assertThat(result.toolCalls()).hasSize(3);
    }

    @Test
    void listenerReceivesPerCycleEvents() {
        var toolSpec = ToolSpecification.builder()
                .name("test_tool")
                .description("Test tool")
                .build();
        var tool = new Tool(toolSpec, (name, args) -> "result");
        var chatModel = mock(ChatModel.class);

        var toolRequest = ToolExecutionRequest.builder()
                .id("call-1")
                .name("test_tool")
                .arguments("{}")
                .build();
        var response1 = ChatResponse.builder()
                .aiMessage(AiMessage.from(toolRequest))
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(new TokenUsage(10, 5))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build())
                .build();
        var response2 = ChatResponse.builder()
                .aiMessage(AiMessage.from("Done."))
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(new TokenUsage(15, 8))
                        .finishReason(FinishReason.STOP)
                        .build())
                .build();

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response1)
                .thenReturn(response2);

        List<ToolUseCycleEvent> events = new ArrayList<>();
        var config = new ToolUseConfig(chatModel, "You are helpful.", List.of(tool), 10, events::add);
        var loop = new ToolUseLoop();
        loop.run(config, "Do it");

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().cycleIndex()).isEqualTo(0);
        assertThat(events.getFirst().toolCalls()).hasSize(1);
        assertThat(events.getFirst().toolCalls().getFirst().name()).isEqualTo("test_tool");
    }

    @Test
    void listenerExceptionDoesNotAbortLoop() {
        var toolSpec = ToolSpecification.builder()
                .name("test_tool")
                .description("Test tool")
                .build();
        var tool = new Tool(toolSpec, (name, args) -> "result");
        var chatModel = mock(ChatModel.class);

        var toolRequest = ToolExecutionRequest.builder()
                .id("call-1")
                .name("test_tool")
                .arguments("{}")
                .build();
        var response1 = ChatResponse.builder()
                .aiMessage(AiMessage.from(toolRequest))
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(new TokenUsage(10, 5))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build())
                .build();
        var response2 = ChatResponse.builder()
                .aiMessage(AiMessage.from("Done."))
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(new TokenUsage(15, 8))
                        .finishReason(FinishReason.STOP)
                        .build())
                .build();

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response1)
                .thenReturn(response2);

        ToolUseListener throwingListener = event -> {
            throw new RuntimeException("Listener failure");
        };
        var config = new ToolUseConfig(chatModel, "You are helpful.", List.of(tool), 10, throwingListener);
        var loop = new ToolUseLoop();
        var result = loop.run(config, "Do it");

        assertThat(result.reason()).isEqualTo(TerminationReason.COMPLETED);
        assertThat(result.text()).isEqualTo("Done.");
    }

    @Test
    void tokenCountsAccumulateAcrossCycles() {
        var toolSpec = ToolSpecification.builder()
                .name("tool")
                .description("Tool")
                .build();
        var tool = new Tool(toolSpec, (name, args) -> "ok");
        var chatModel = mock(ChatModel.class);

        var toolRequest = ToolExecutionRequest.builder()
                .id("call-1")
                .name("tool")
                .arguments("{}")
                .build();
        var response1 = ChatResponse.builder()
                .aiMessage(AiMessage.from(toolRequest))
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(new TokenUsage(100, 50))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build())
                .build();
        var response2 = ChatResponse.builder()
                .aiMessage(AiMessage.from(toolRequest))
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(new TokenUsage(200, 80))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build())
                .build();
        var response3 = ChatResponse.builder()
                .aiMessage(AiMessage.from("Final answer"))
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(new TokenUsage(300, 120))
                        .finishReason(FinishReason.STOP)
                        .build())
                .build();

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response1)
                .thenReturn(response2)
                .thenReturn(response3);

        var config = new ToolUseConfig(chatModel, "System", List.of(tool));
        var loop = new ToolUseLoop();
        var result = loop.run(config, "Go");

        assertThat(result.inputTokens()).isEqualTo(600);
        assertThat(result.outputTokens()).isEqualTo(250);
        assertThat(result.cycleCount()).isEqualTo(2);
    }

    @Test
    void immediateTextResponseWithNoToolCalls() {
        var chatModel = mock(ChatModel.class);

        var response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Direct answer"))
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(new TokenUsage(10, 5))
                        .finishReason(FinishReason.STOP)
                        .build())
                .build();

        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response);

        var config = new ToolUseConfig(chatModel, "System", List.of());
        var loop = new ToolUseLoop();
        var result = loop.run(config, "Hello");

        assertThat(result.reason()).isEqualTo(TerminationReason.COMPLETED);
        assertThat(result.text()).isEqualTo("Direct answer");
        assertThat(result.cycleCount()).isEqualTo(0);
        assertThat(result.toolCalls()).isEmpty();
    }
}
