package io.casehub.yaml.core.type;

import io.casehub.yaml.core.condition.Truthiness;

import java.util.Set;

public enum ValueType {
    STRING, INTEGER, BOOLEAN, NUMBER;

    public Object parse(String value) {
        return switch (this) {
            case STRING  -> value;
            case INTEGER -> Integer.parseInt(value);
            case BOOLEAN -> Truthiness.isTruthy(value);
            case NUMBER  -> Double.parseDouble(value);
        };
    }

    public Set<String> javaTypes() {
        return switch (this) {
            case STRING  -> Set.of("java.lang.String", "java.lang.CharSequence");
            case INTEGER -> Set.of("int", "java.lang.Integer", "long", "java.lang.Long",
                                   "java.lang.Number");
            case BOOLEAN -> Set.of("boolean", "java.lang.Boolean");
            case NUMBER  -> Set.of("double", "java.lang.Double", "float", "java.lang.Float",
                                   "java.math.BigDecimal", "java.lang.Number");
        };
    }

    public boolean accepts(String javaTypeName) { return javaTypes().contains(javaTypeName); }

    public static ValueType fromString(String name) {
        return switch (name.toUpperCase(java.util.Locale.ROOT)) {
            case "STRING" -> STRING;
            case "INTEGER" -> INTEGER;
            case "NUMBER", "DECIMAL" -> NUMBER;
            case "BOOLEAN" -> BOOLEAN;
            default -> null;
        };
    }

    public boolean validate(Object value) {
        if (value == null) {return false;}
        return switch (this) {
            case STRING -> value instanceof String;
            case INTEGER -> value instanceof Integer || value instanceof Long;
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
        };
    }

}
