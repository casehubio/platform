package io.casehub.template.registry.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import io.casehub.template.registry.AdaptiveFallbackMatcher;
import io.casehub.template.registry.ClassificationResult;
import io.casehub.template.registry.TemplateCandidate;
import io.casehub.yaml.core.resolver.VariableConstraint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LlmAdaptiveFallback implements AdaptiveFallbackMatcher {

    private static final Logger LOG = Logger.getLogger(LlmAdaptiveFallback.class.getName());
    private static final String SYSTEM_PROMPT = "You classify natural language input against template patterns. For each template, determine if the input is semantically equivalent. If yes, extract variable values and suggest a normalised pattern with ${variable} placeholders preserved. Return JSON: {\"match\": true/false, \"templateName\": \"...\", \"fields\": {...}, \"suggestedVariation\": \"...\", \"confidence\": 0.0-1.0}. If no match, return {\"match\": false}.";

    private final ChatModel chatModel;

    public LlmAdaptiveFallback(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public ClassificationResult classify(String input, List<TemplateCandidate> candidates, String language) {
        var sb = new StringBuilder();
        sb.append("Input: \"").append(input).append("\"\nLanguage: ").append(language).append("\n\nTemplates:\n");
        for (var c : candidates) {
            sb.append("  - name: ").append(c.templateName()).append("\n    patterns: ").append(c.patterns()).append("\n");
            if (!c.variables().isEmpty()) {
                sb.append("    variables:\n");
                for (var v : c.variables().entrySet()) {
                    sb.append("      ").append(v.getKey()).append(": ");
                    if (v.getValue() instanceof VariableConstraint.EnumConstraint ec) sb.append("enum ").append(ec.values());
                    else sb.append("string");
                    sb.append("\n");
                }
            }
        }
        sb.append("\nReturn JSON classification result.");

        var req = ChatRequest.builder().messages(List.of(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(sb.toString()))).build();
        String text = chatModel.chat(req).aiMessage().text();
        return parseResult(text);
    }

    ClassificationResult parseResult(String response) {
        String t = response.strip();
        if (t.startsWith("```")) {
            int s = t.indexOf('{'); int e = t.lastIndexOf('}');
            if (s >= 0 && e > s) t = t.substring(s, e + 1);
        }
        if (!t.startsWith("{")) return new ClassificationResult.Unmatched();

        boolean match = extractBoolean(t, "match");
        if (!match) return new ClassificationResult.Unmatched();

        String templateName = extractString(t, "templateName");
        String suggestedVariation = extractString(t, "suggestedVariation");
        double confidence = extractDouble(t, "confidence", 0.5);
        Map<String, String> fields = extractFields(t);

        if (templateName == null || suggestedVariation == null) return new ClassificationResult.Unmatched();
        return new ClassificationResult.Matched(templateName, fields, suggestedVariation, confidence);
    }

    private boolean extractBoolean(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return false;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return false;
        String after = json.substring(colon + 1).strip();
        return after.startsWith("true");
    }

    private String extractString(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return null;
        String after = json.substring(colon + 1).strip();
        if (!after.startsWith("\"")) return null;
        int end = after.indexOf('"', 1);
        if (end < 0) return null;
        return after.substring(1, end);
    }

    private double extractDouble(String json, String key, double defaultVal) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return defaultVal;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return defaultVal;
        String after = json.substring(colon + 1).strip();
        var sb = new StringBuilder();
        for (int i = 0; i < after.length(); i++) {
            char c = after.charAt(i);
            if (Character.isDigit(c) || c == '.') sb.append(c);
            else break;
        }
        try { return Double.parseDouble(sb.toString()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private Map<String, String> extractFields(String json) {
        Map<String, String> fields = new LinkedHashMap<>();
        int                 idx    = json.indexOf("\"fields\"");
        if (idx < 0) {return fields;}
        int braceStart = json.indexOf('{', idx);
        if (braceStart < 0) {return fields;}
        int depth    = 0;
        int braceEnd = -1;
        for (int i = braceStart; i < json.length(); i++) {
            if (json.charAt(i) == '{') {depth++;} else if (json.charAt(i) == '}') {
                depth--;
                if (depth == 0) {
                    braceEnd = i;
                    break;
                }
            }
        }
        if (braceEnd < 0) {return fields;}
        String       inner     = json.substring(braceStart + 1, braceEnd).strip();
        boolean      inStr     = false;
        boolean      esc       = false;
        int          pairStart = 0;
        List<String> pairs     = new ArrayList<>();
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (esc) {
                esc = false;
                continue;
            }
            if (c == '\\') {
                esc = true;
                continue;
            }
            if (c == '"') {inStr = !inStr;} else if (!inStr && c == ',') {
                pairs.add(inner.substring(pairStart, i));
                pairStart = i + 1;
            }
        }
        if (pairStart < inner.length()) {pairs.add(inner.substring(pairStart));}
        for (String pair : pairs) {
            int colon = pair.indexOf(':');
            if (colon < 0) {continue;}
            String k = pair.substring(0, colon).strip().replace("\"", "");
            String v = pair.substring(colon + 1).strip();
            if (v.startsWith("\"") && v.endsWith("\"")) {
                v = v.substring(1, v.length() - 1).replace("\\\"", "\"");
            }
            if (!k.isBlank()) {fields.put(k, v);}
        }
        return fields;
    }
}
