package io.casehub.yaml.core.module;

import io.casehub.yaml.core.condition.Truthiness;

import java.util.Arrays;

public enum ParameterType {
    STRING, LIST, INTEGER, NUMBER, BOOLEAN;

    public Object parse(String value) {
        return switch (this) {
            case STRING -> value;
            case LIST -> Arrays.stream(value.split(","))
                    .map(String::trim).toList();
            case INTEGER -> Integer.parseInt(value);
            case NUMBER -> Double.parseDouble(value);
            case BOOLEAN -> Truthiness.isTruthy(value);
        };
    }

    public boolean canAccept(ParameterType outputType) {
        if (this == outputType) {return true;}
        if (this == STRING && outputType != LIST) {return true;}
        if (this == NUMBER && outputType == INTEGER) {return true;}
        return false;
    }

}
