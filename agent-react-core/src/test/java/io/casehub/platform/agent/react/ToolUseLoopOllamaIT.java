package io.casehub.platform.agent.react;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.casehub.platform.agent.openai.OpenAiNativeChatModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "OLLAMA_AVAILABLE", matches = "true")
class ToolUseLoopOllamaIT {

    @Test
    void toolUseLoopWithOllamaChatModel() {
        OpenAIClient openAiClient = OpenAIOkHttpClient.builder()
                .baseUrl("http://localhost:11434/v1/")
                .apiKey("ollama")
                .build();
        var chatModel = new OpenAiNativeChatModel(openAiClient, "qwen3:8b", Duration.ofSeconds(120));

        var toolSpec = ToolSpecification.builder()
                .name("get_current_time")
                .description("Get the current time in a given timezone. Pass timezone as a JSON argument.")
                .build();

        var tool = new Tool(toolSpec, (name, args) -> "{\"time\": \"2026-10-19T14:30:00Z\", \"timezone\": \"UTC\"}");

        List<ToolUseCycleEvent> events = new ArrayList<>();
        var config = new ToolUseConfig(chatModel, "You are a helpful assistant. Use tools when needed.",
                List.of(tool), 5, events::add);

        var loop = new ToolUseLoop();
        var result = loop.run(config, "What time is it in UTC?");

        assertThat(result.reason()).isIn(TerminationReason.COMPLETED, TerminationReason.MAX_CYCLES_REACHED);
        assertThat(result.inputTokens()).isGreaterThan(0);
        assertThat(result.outputTokens()).isGreaterThan(0);
        assertThat(result.elapsed()).isPositive();

        if (result.reason() == TerminationReason.COMPLETED) {
            assertThat(result.text()).isNotBlank();
        }

        System.out.println("Ollama IT result:");
        System.out.println("  Reason: " + result.reason());
        System.out.println("  Cycles: " + result.cycleCount());
        System.out.println("  Tool calls: " + result.toolCalls().size());
        System.out.println("  Tokens: " + result.inputTokens() + " in / " + result.outputTokens() + " out");
        System.out.println("  Elapsed: " + result.elapsed());
        if (result.text() != null) {
            System.out.println("  Text: " + result.text().substring(0, Math.min(200, result.text().length())));
        }
    }
}
