package io.casehub.yaml.core.step;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public enum StepParameterType {
    STRING, INTEGER, NUMBER, BOOLEAN, ARRAY, OBJECT;


    public io.casehub.yaml.core.type.ValueType scalarType() {
        return switch (this) {
            case STRING -> io.casehub.yaml.core.type.ValueType.STRING;
            case INTEGER -> io.casehub.yaml.core.type.ValueType.INTEGER;
            case NUMBER -> io.casehub.yaml.core.type.ValueType.NUMBER;
            case BOOLEAN -> io.casehub.yaml.core.type.ValueType.BOOLEAN;
            case ARRAY, OBJECT -> null;
        };
    }

    public static StepParameterType fromValueType(io.casehub.yaml.core.type.ValueType vt) {
        return switch (vt) {
            case STRING -> STRING;
            case INTEGER -> INTEGER;
            case NUMBER -> NUMBER;
            case BOOLEAN -> BOOLEAN;
        };
    }

    public static StepParameterType fromString(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if ("ARRAY".equals(upper)) {return ARRAY;}
        if ("OBJECT".equals(upper)) {return OBJECT;}
        io.casehub.yaml.core.type.ValueType vt = io.casehub.yaml.core.type.ValueType.fromString(name);
        if (vt == null) {
            throw new IllegalArgumentException(
                    "Unknown step parameter type '" + name
                    + "'. Expected: STRING, INTEGER, NUMBER, BOOLEAN, ARRAY, OBJECT.");
        }
        return fromValueType(vt);
    }

    public boolean isScalar() {return scalarType() != null;}

    public boolean validate(Object value) {
        io.casehub.yaml.core.type.ValueType vt = scalarType();
        if (vt != null) {return vt.validate(value);}
        return switch (this) {
            case ARRAY -> value instanceof List;
            case OBJECT -> value instanceof Map;
            default -> throw new AssertionError("Unexpected type: " + this);
        };
    }

    public Object parseScalar(String value) {
        io.casehub.yaml.core.type.ValueType vt = scalarType();
        if (vt == null) {
            throw new IllegalArgumentException(
                    "Cannot parse '" + this + "' from string — "
                    + "defaults are only supported for scalar types");
        }
        return vt.parse(value);
    }
}
