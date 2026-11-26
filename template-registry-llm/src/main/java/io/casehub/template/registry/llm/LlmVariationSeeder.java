package io.casehub.template.registry.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.VariationSeeder;
import io.casehub.yaml.core.resolver.VariableConstraint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LlmVariationSeeder implements VariationSeeder {

    private static final Logger LOG = Logger.getLogger(LlmVariationSeeder.class.getName());
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final ChatModel chatModel;
    private final int maxVariationsPerPattern;

    public LlmVariationSeeder(ChatModel chatModel, int maxVariationsPerPattern) {
        this.chatModel               = chatModel;
        this.maxVariationsPerPattern = maxVariationsPerPattern;
    }

    public LlmVariationSeeder(ChatModel chatModel) {
        this(chatModel, 5);
    }

    @Override
    public List<String> seed(TemplateDefinition template, String language) {
        List<String> allVariations = new ArrayList<>();
        for (String pattern : template.patterns()) {
            List<String> variations = seedPattern(pattern, template.variables(), language);
            allVariations.addAll(variations);
        }
        return List.copyOf(allVariations);
    }

    private List<String> seedPattern(String pattern,
                                      Map<String, VariableConstraint> variables,
                                      String language) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(pattern, variables, language);

        try {
            var request = ChatRequest.builder()
                .messages(List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt)))
                .build();

            var response = chatModel.chat(request);
            String text = response.aiMessage().text();
            return parseAndValidate(text, pattern, variables);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to seed variations for pattern: " + pattern, e);
            return List.of();
        }
    }

    private String buildSystemPrompt() {
        return "You generate semantic paraphrases of template patterns. "
            + "Rules: "
            + "1. Preserve ALL ${variable} placeholders exactly as they appear. "
            + "2. Each variation must be natural language. "
            + "3. Rearrange structure, use synonyms for literal words, but NEVER "
            + "substitute or remove a ${variable} placeholder. "
            + "4. Return ONLY a JSON array of strings, no other text.";
    }

    private String buildUserPrompt(String pattern,
                                    Map<String, VariableConstraint> variables,
                                    String language) {
        var sb = new StringBuilder();
        sb.append("Generate up to ").append(maxVariationsPerPattern)
          .append(" semantic paraphrases of this template pattern.\n\n");
        sb.append("Pattern: ").append(pattern).append("\n");
        sb.append("Language: ").append(language).append("\n");
        if (!variables.isEmpty()) {
            sb.append("Variables:\n");
            for (var entry : variables.entrySet()) {
                sb.append("  - ${").append(entry.getKey()).append("}: ");
                if (entry.getValue() instanceof VariableConstraint.EnumConstraint ec) {
                    sb.append("enum ").append(ec.values());
                } else {
                    sb.append("string");
                }
                sb.append("\n");
            }
        }
        sb.append("\nReturn a JSON array of variation strings.");
        return sb.toString();
    }

    List<String> parseAndValidate(String llmResponse,
                                   String originalPattern,
                                   Map<String, VariableConstraint> variables) {
        String trimmed = llmResponse.strip();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('[');
            int end = trimmed.lastIndexOf(']');
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start, end + 1);
            }
        }

        List<String> results = new ArrayList<>();
        try {
            if (!trimmed.startsWith("[")) return List.of();
            String content = trimmed.substring(1, trimmed.length() - 1);
            for (String item : splitJsonArray(content)) {
                String variation = item.strip();
                if (variation.startsWith("\"") && variation.endsWith("\"")) {
                    variation = variation.substring(1, variation.length() - 1)
                        .replace("\\\"", "\"")
                        .replace("\\n", "\n");
                }
                if (preservesAllVariables(variation, variables)
                    && !variation.equals(originalPattern)) {
                    results.add(variation);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse LLM response: " + llmResponse, e);
        }
        return results.stream().limit(maxVariationsPerPattern).toList();
    }

    private List<String> splitJsonArray(String content) {
        List<String> items = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        int start = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
            } else if (!inString) {
                if (c == '[' || c == '{') depth++;
                else if (c == ']' || c == '}') depth--;
                else if (c == ',' && depth == 0) {
                    items.add(content.substring(start, i));
                    start = i + 1;
                }
            }
        }
        if (start < content.length()) {
            items.add(content.substring(start));
        }
        return items;
    }

    private boolean preservesAllVariables(String variation,
                                           Map<String, VariableConstraint> variables) {
        for (String varName : variables.keySet()) {
            if (!variation.contains("${" + varName + "}")) {
                return false;
            }
        }
        return true;
    }
}
