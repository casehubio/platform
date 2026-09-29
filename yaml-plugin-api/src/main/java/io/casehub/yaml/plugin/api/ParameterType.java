package io.casehub.yaml.plugin.api;

import java.util.Locale;

public enum ParameterType {
    STRING, INTEGER, NUMBER, BOOLEAN, ARRAY, OBJECT;

    public boolean isScalar() {
        return this != ARRAY && this != OBJECT;
    }

    public boolean validate(Object value) {
        return switch (this) {
            case STRING  -> value instanceof String;
            case INTEGER -> value instanceof Integer || value instanceof Long;
            case NUMBER  -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case ARRAY   -> value instanceof java.util.List;
            case OBJECT  -> value instanceof java.util.Map;
        };
    }

    public Object parseScalar(String value) {
        return switch (this) {
            case STRING  -> value;
            case INTEGER -> Integer.parseInt(value);
            case NUMBER  -> Double.parseDouble(value);
            case BOOLEAN -> switch (value.toLowerCase(Locale.ROOT)) {
                case "true", "yes", "on", "y", "1" -> true;
                case "false", "no", "off", "n", "0" -> false;
                default -> throw new IllegalArgumentException(
                        "'" + value + "' is not a boolean value. "
                        + "Expected: true/false/yes/no/on/off/y/n/1/0");
            };
            case ARRAY, OBJECT -> throw new IllegalArgumentException(
                    "Cannot parse '" + this + "' from string");
        };
    }

    public boolean canAccept(ParameterType outputType) {
        if (this == outputType) return true;
        if (this == STRING && outputType.isScalar()) return true;
        if (this == NUMBER && outputType == INTEGER) return true;
        return false;
    }

    public static ParameterType fromString(String name) {
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "STRING" -> STRING;
            case "INTEGER" -> INTEGER;
            case "NUMBER", "DECIMAL" -> NUMBER;
            case "BOOLEAN" -> BOOLEAN;
            case "ARRAY", "LIST" -> ARRAY;
            case "OBJECT" -> OBJECT;
            default -> throw new IllegalArgumentException(
                    "Unknown parameter type '" + name + "'");
        };
    }
}
