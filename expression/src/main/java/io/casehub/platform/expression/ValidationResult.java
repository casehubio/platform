package io.casehub.platform.expression;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record ValidationResult(boolean ok, String error, List<JsonNode> output) {

    public static ValidationResult ok(List<JsonNode> out) {
        return new ValidationResult(true, null, out);
    }

    public static ValidationResult error(String msg) {
        return new ValidationResult(false, msg, null);
    }

    public boolean isTrue() {
        if (!ok || output == null || output.isEmpty()) return false;
        for (JsonNode node : output) {
            if (node.isBoolean() && node.asBoolean()) return true;
        }
        return false;
    }
}
