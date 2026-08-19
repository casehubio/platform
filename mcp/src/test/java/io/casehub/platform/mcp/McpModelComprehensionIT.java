package io.casehub.platform.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM comprehension test for the hierarchical MCP model.
 *
 * <p>Validates that an LLM agent can navigate {@code casehub_model} output
 * and construct correct {@code casehub_action} calls — the core premise of
 * protocol #407 (two tools replace N per-domain tools).
 *
 * <p>Requires Claude CLI installed and authenticated. Skipped automatically
 * when unavailable. Swap to LangChain4j + Ollama via {@code *IT.java} +
 * failsafe when platform#65 CI infra lands.
 */
@QuarkusTest
@EnabledIf("claudeAvailable")
class McpModelComprehensionIT {

    private static final String SYSTEM_PROMPT =
            "You are a test harness validating MCP tool comprehension. "
            + "Respond with ONLY the requested JSON — no markdown fences, "
            + "no explanation, no commentary. Raw JSON only.";

    @Inject
    CaseHubMcpTools tools;

    @Inject
    ReflectiveOperationDispatcher dispatcher;

    @Inject
    AgentProvider agentProvider;

    private final ObjectMapper mapper = new ObjectMapper();

    static boolean claudeAvailable() {
        try {
            Process p = new ProcessBuilder("claude", "--version")
                    .redirectErrorStream(true).start();
            boolean finished = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) { p.destroyForcibly(); return false; }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void selectsCorrectDomainFromTier0() throws Exception {
        String tier0 = tools.casehub_model(null);

        String response = askClaude(
                "Here is the CaseHub operation catalog:\n" + tier0
                + "\n\nI need to echo a message back. Which domain should I use? "
                + "Reply with JSON: {\"domain\": \"<name>\"}");

        Map<String, Object> result = parseJson(response);
        assertThat(result.get("domain")).isEqualTo("test");
    }

    @Test
    void constructsCorrectActionFromTier1() throws Exception {
        String tier1 = tools.casehub_model("test");

        String response = askClaude(
                "Here are the operations for the 'test' domain:\n" + tier1
                + "\n\nI want to echo the message 'hello world'. "
                + "Construct the casehub_action call. Reply with JSON: "
                + "{\"domain\": \"test\", \"operation\": \"<name>\", \"params\": {...}}");

        Map<String, Object> result = parseJson(response);
        assertThat(result.get("domain")).isEqualTo("test");
        assertThat(result.get("operation")).isEqualTo("echo");

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) result.get("params");
        assertThat(params).containsEntry("message", "hello world");
    }

    @Test
    void constructsParamsForComplexInputType() throws Exception {
        String tier1 = tools.casehub_model("test");

        String response = askClaude(
                "Here are the operations for the 'test' domain:\n" + tier1
                + "\n\nI want to create something with name 'widget' and count 5. "
                + "Construct the casehub_action call. Reply with JSON: "
                + "{\"domain\": \"test\", \"operation\": \"<name>\", \"params\": {...}}");

        Map<String, Object> result = parseJson(response);
        assertThat(result.get("domain")).isEqualTo("test");
        assertThat(result.get("operation")).isEqualTo("create");

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) result.get("params");
        assertThat(params).containsKey("input");

        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) params.get("input");
        assertThat(input).containsEntry("name", "widget");
        assertThat(((Number) input.get("count")).intValue()).isEqualTo(5);
    }

    @Test
    void endToEndDispatchWithLlmConstructedParams() throws Exception {
        String tier1 = tools.casehub_model("test");

        String response = askClaude(
                "Here are the operations for the 'test' domain:\n" + tier1
                + "\n\nI want to echo 'round-trip works'. "
                + "Reply with JSON: "
                + "{\"domain\": \"test\", \"operation\": \"<name>\", \"params\": {...}}");

        Map<String, Object> action = parseJson(response);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = action.get("params") != null
                ? (Map<String, Object>) action.get("params") : Map.of();
        Object actionResult = dispatcher.dispatch(
                (String) action.get("domain"),
                (String) action.get("operation"),
                params);

        assertThat(actionResult.toString()).contains("round-trip works");
    }

    private String askClaude(String userPrompt) {
        var config = AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt,
                Duration.ofSeconds(30));
        return agentProvider.invoke(config)
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .collect().with(Collectors.joining())
                .await().atMost(Duration.ofSeconds(60));
    }

    private Map<String, Object> parseJson(String response) throws Exception {
        String cleaned = response.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "");
        }
        return mapper.readValue(cleaned.strip(), new TypeReference<>() {});
    }
}
