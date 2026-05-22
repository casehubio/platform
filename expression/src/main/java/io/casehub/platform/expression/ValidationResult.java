package io.casehub.platform.expression;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record ValidationResult(boolean ok, String error, List<JsonNode> output) {

    public ValidationResult {
        if (ok && output == null) throw new IllegalArgumentException("ok result requires output list");
        if (!ok && error == null) throw new IllegalArgumentException("error result requires error message");
    }

    public static ValidationResult ok(List<JsonNode> out) {
        return new ValidationResult(true, null, out);
    }

    public static ValidationResult error(String msg) {
        return new ValidationResult(false, msg, List.of());
    }

    /**
     * Returns {@code true} if the expression produced at least one output node whose
     * value is the JSON boolean {@code true}. Only the literal JSON boolean {@code true}
     * qualifies — non-null strings, numbers, and objects do not. Callers that need
     * JQ-style truthiness (any non-null, non-false value) must inspect {@code output()}
     * directly.
     */
    public boolean isTrue() {
        if (!ok || output.isEmpty()) return false;
        for (JsonNode node : output) {
            if (node.isBoolean() && node.asBoolean()) return true;
        }
        return false;
    }
}
