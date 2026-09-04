package io.casehub.yaml.core.module;

import io.casehub.yaml.core.condition.Truthiness;

import java.util.Arrays;

public enum ParameterType {
    STRING, LIST, INTEGER, NUMBER, BOOLEAN;

    public ParsedValue parse(String value) {
        return switch (this) {
            case STRING -> new ParsedValue.StringValue(value);
            case LIST -> new ParsedValue.ListValue(Arrays.stream(value.split(","))
                                                                   .map(String::trim).toList());
            case INTEGER -> new ParsedValue.IntegerValue(Integer.parseInt(value));
            case NUMBER -> new ParsedValue.NumberValue(Double.parseDouble(value));
            case BOOLEAN -> new ParsedValue.BooleanValue(Truthiness.isTruthy(value));
        };
    }

    public boolean canAccept(ParameterType outputType) {
        if (this == outputType) {return true;}
        if (this == STRING && outputType != LIST) {return true;}
        if (this == NUMBER && outputType == INTEGER) {return true;}
        return false;
    }

    public static ParameterType fromString(String name) {
        return switch (name.toUpperCase(java.util.Locale.ROOT)) {
            case "STRING" -> STRING;
            case "LIST" -> LIST;
            case "INTEGER" -> INTEGER;
            case "NUMBER", "DECIMAL" -> NUMBER;
            case "BOOLEAN" -> BOOLEAN;
            default -> throw new IllegalArgumentException(
                    "Unknown parameter type '" + name + "'. Expected: STRING, INTEGER, NUMBER, BOOLEAN, LIST, DECIMAL.");
        };
    }


}
