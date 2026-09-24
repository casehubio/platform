package io.casehub.yaml.core.type;

import java.util.Map;

public record TypedMap(Map<String, ValueType> schema, Map<String, Object> values) implements TypedSchema {
    public TypedMap {
        schema = Map.copyOf(schema);
        values = Map.copyOf(values);
    }

    @Override
    public ValueType typeOf(String name) { return schema.get(name); }
}
