package io.casehub.yaml.core.step;

import io.casehub.yaml.core.condition.Truthiness;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public enum StepParameterType {
    STRING, INTEGER, NUMBER, BOOLEAN, ARRAY, OBJECT;

    public static StepParameterType fromString(String name) {
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "STRING" -> STRING;
            case "INTEGER" -> INTEGER;
            case "NUMBER", "DECIMAL" -> NUMBER;
            case "BOOLEAN" -> BOOLEAN;
            case "ARRAY" -> ARRAY;
            case "OBJECT" -> OBJECT;
            default -> throw new IllegalArgumentException(
                    "Unknown step parameter type '" + name
                    + "'. Expected: STRING, INTEGER, NUMBER, BOOLEAN, ARRAY, OBJECT.");
        };
    }

    public boolean isScalar() {
        return this != ARRAY && this != OBJECT;
    }

    public boolean validate(Object value) {
        return switch (this) {
            case STRING  -> value instanceof String;
            case INTEGER -> value instanceof Integer || value instanceof Long;
            case NUMBER  -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case ARRAY   -> value instanceof List;
            case OBJECT  -> value instanceof Map;
        };
    }

    public Object parseScalar(String value) {
        return switch (this) {
            case STRING  -> value;
            case INTEGER -> Integer.parseInt(value);
            case NUMBER  -> Double.parseDouble(value);
            case BOOLEAN -> Truthiness.isTruthy(value);
            case ARRAY, OBJECT -> throw new IllegalArgumentException(
                    "Cannot parse '" + this + "' from string — "
                    + "defaults are only supported for scalar types");
        };
    }
}
