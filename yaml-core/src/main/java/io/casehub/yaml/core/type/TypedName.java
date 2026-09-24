package io.casehub.yaml.core.type;

import java.util.Locale;

public record TypedName(String name, ValueType type) {
    public TypedName {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Variable name must not be blank");
    }

    public static TypedName parse(String raw) {
        int colon = raw.indexOf(':');
        if (colon < 0) return new TypedName(raw.trim(), ValueType.STRING);
        String name = raw.substring(0, colon).trim();
        String typePart = raw.substring(colon + 1).trim();
        if (typePart.isEmpty())
            throw new IllegalArgumentException(
                "Missing type after ':' in '" + raw + "'. Expected: STRING, INTEGER, BOOLEAN, NUMBER.");
        if (name.isBlank())
            throw new IllegalArgumentException("Variable name must not be blank");
        try {
            return new TypedName(name, ValueType.valueOf(typePart.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Unknown type '" + typePart + "' in '" + raw
                + "'. Expected: STRING, INTEGER, BOOLEAN, NUMBER.");
        }
    }
}
