package io.casehub.yaml.core.module;

import io.casehub.yaml.plugin.api.ParameterType;
import io.casehub.yaml.core.condition.Truthiness;

import java.util.Arrays;

public final class ModuleParameterParser {

    private ModuleParameterParser() {}

    public static ParsedValue parse(ParameterType type, String value) {
        return switch (type) {
            case STRING  -> new ParsedValue.StringValue(value);
            case ARRAY   -> new ParsedValue.ListValue(
                    Arrays.stream(value.split(",")).map(String::trim).toList());
            case INTEGER -> new ParsedValue.IntegerValue(Integer.parseInt(value));
            case NUMBER  -> new ParsedValue.NumberValue(Double.parseDouble(value));
            case BOOLEAN -> new ParsedValue.BooleanValue(Truthiness.isTruthy(value));
            case OBJECT  -> throw new IllegalArgumentException(
                    "Module parameters do not support OBJECT type");
        };
    }
}
