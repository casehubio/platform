package io.casehub.yaml.step.runner;

import io.casehub.yaml.core.error.ParseError;
import io.casehub.yaml.core.error.YamlError;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Parameter;
import io.casehub.yaml.plugin.api.ParameterType;
import io.casehub.yaml.plugin.api.PluginRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ValidationSuggestionGenerator {

    private ValidationSuggestionGenerator() {}

    public static List<ValidationSuggestion> generate(List<YamlError> diagnostics, PluginRegistry registry) {
        List<ValidationSuggestion> suggestions = new ArrayList<>();
        for (int i = 0; i < diagnostics.size(); i++) {
            YamlError error = diagnostics.get(i);
            if (error instanceof ParseError.UnknownStepError unknown) {
                String closest = findClosest(unknown.referencedAction(), registry);
                if (closest != null) {
                    String example = buildExample(closest, registry);
                    suggestions.add(new ValidationSuggestion(i,
                            "Did you mean '" + closest + "'?", example));
                }
            } else if (error instanceof ParseError.ParameterError paramErr) {
                var def = registry.resolve(paramErr.stepName());
                if (def.isPresent()) {
                    String example = buildExample(paramErr.stepName(), registry);
                    suggestions.add(new ValidationSuggestion(i,
                            "Check parameter '" + paramErr.parameterName() + "' on '" + paramErr.stepName() + "'",
                            example));
                }
            }
        }
        return suggestions;
    }

    static String findClosest(String input, PluginRegistry registry) {
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String action : registry.availableActions()) {
            int dist = levenshtein(input.toLowerCase(), action.toLowerCase());
            if (dist < bestDist && dist <= Math.max(input.length(), action.length()) / 2) {
                bestDist = dist;
                best = action;
            }
        }
        return best;
    }

    static String buildExample(String actionName, PluginRegistry registry) {
        var def = registry.resolve(actionName);
        if (def.isEmpty()) return null;
        Definition d = def.get();
        Map<String, Parameter> inputs = d.inputs();
        if (inputs.isEmpty()) {
            return "- " + actionName + ": {}";
        }
        var sb = new StringBuilder("- ").append(actionName).append(":\n");
        for (var entry : inputs.entrySet()) {
            String placeholder = placeholderFor(entry.getValue().type());
            sb.append("    ").append(entry.getKey()).append(": ").append(placeholder).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private static String placeholderFor(ParameterType type) {
        if (type == null) return "\"value\"";
        return switch (type) {
            case STRING -> "\"value\"";
            case INTEGER -> "0";
            case NUMBER -> "0.0";
            case BOOLEAN -> "true";
            case ARRAY -> "[]";
            case OBJECT -> "{}";
            case EXPRESSION -> "\"${value}\"";
        };
    }

    static int levenshtein(String a, String b) {
        int m = a.length(), n = b.length();
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        for (int j = 0; j <= n; j++) prev[j] = j;
        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            var tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[n];
    }
}