package io.casehub.template.registry.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.casehub.template.registry.CorpusQualityReport;
import io.casehub.template.registry.InMemoryTemplateRegistry;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.TemplateReport;
import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "casehub.ollama.host", matches = ".+")
class CorpusQualityOllamaIT {

    @TempDir
    static Path outputDir;

    private static ChatModel chatModel;

    @BeforeAll
    static void setUpModel() {
        String host = System.getProperty("casehub.ollama.host", "http://localhost:11434");
        String model = System.getProperty("casehub.ollama.model", "qwen2.5:1.5b");
        chatModel = new OllamaDirectChatModel(host, model);
    }

    @Test
    void fullPipelineWithOllama() {
        var namespace = new TemplateNamespace("test:governance", Map.of(
            "vote", TemplateDefinition.builder("vote")
                .pattern("I ${decision} this ${subject}")
                .variable("decision", VariableConstraint.enumOf("approve", "reject", "abstain"))
                .variable("subject", VariableConstraint.string())
                .build(),
            "delegate", TemplateDefinition.builder("delegate")
                .pattern("delegate ${task} to ${assignee}")
                .variable("task", VariableConstraint.string())
                .variable("assignee", VariableConstraint.string())
                .build()
        ));

        var registry = new InMemoryTemplateRegistry();
        registry.register(namespace);
        var seeder = new LlmVariationSeeder(chatModel, 5);

        Path output = outputDir.resolve("governance.variations.yaml");
        CorpusQualityReport report = CorpusVariationGenerator.run(
            namespace, seeder, registry, output, 0.5);

        assertThat(report.totalVariations()).isGreaterThan(0);
        assertThat(output).exists();

        for (TemplateReport t : report.templates()) {
            System.out.printf("[IT] %s: matchRate=%.1f%% variations=%d matched=%d%n",
                t.templateName(), t.matchRate() * 100,
                t.variationCount(), t.matchedCount());
        }
    }

    private static class OllamaDirectChatModel implements ChatModel {

        private final String host;
        private final String model;
        private final HttpClient client = HttpClient.newHttpClient();

        OllamaDirectChatModel(String host, String model) {
            this.host = host;
            this.model = model;
        }

        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            StringBuilder systemPrompt = new StringBuilder();
            StringBuilder userPrompt   = new StringBuilder();
            for (ChatMessage msg : chatRequest.messages()) {
                if (msg instanceof SystemMessage sm) {
                    systemPrompt.append(sm.text());
                } else if (msg instanceof UserMessage um) {
                    userPrompt.append(um.singleText());
                }
            }

            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var requestNode = mapper.createObjectNode()
                                    .put("model", model)
                                    .put("prompt", userPrompt.toString())
                                    .put("system", systemPrompt.toString())
                                    .put("stream", false);

            try {
                HttpRequest request = HttpRequest.newBuilder()
                                                 .uri(URI.create(host + "/api/generate"))
                                                 .header("Content-Type", "application/json")
                                                 .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestNode)))
                                                 .build();

                HttpResponse<String> response     = client.send(request, HttpResponse.BodyHandlers.ofString());
                var                  responseNode = mapper.readTree(response.body());
                String               text         = responseNode.get("response").asText();

                return ChatResponse.builder()
                                   .aiMessage(AiMessage.from(text))
                                   .build();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Ollama request failed", e);
            }
        }
    }
}
