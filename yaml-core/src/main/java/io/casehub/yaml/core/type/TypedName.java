package io.casehub.yaml.core.type;

public record TypedName(String name, String typeName, ValueType scalarType) {
    public TypedName {
        if (name == null || name.isBlank()) {throw new IllegalArgumentException("Variable name must not be blank");}
        if (typeName == null || typeName.isBlank()) {throw new IllegalArgumentException("Type name must not be blank");}
    }

    public boolean isScalar() {return scalarType != null;}

    public static TypedName parse(String raw) {
        int colon = raw.indexOf(':');
        if (colon < 0) {return new TypedName(raw.trim(), "string", ValueType.STRING);}
        String name     = raw.substring(0, colon).trim();
        String typePart = raw.substring(colon + 1).trim();
        if (typePart.isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing type after ':' in '" + raw + "'.");
        }
        if (name.isBlank()) {throw new IllegalArgumentException("Variable name must not be blank");}
        ValueType scalar = ValueType.fromString(typePart);
        return new TypedName(name, typePart, scalar);
    }
}
