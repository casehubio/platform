package io.casehub.yaml.core.type;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record TypedMap(Map<String, ValueType> schema,
                       Map<String, Object> values,
                       Map<String, String> domainTypes) implements TypedSchema {
    public TypedMap {
        schema      = Map.copyOf(schema);
        values      = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        domainTypes = domainTypes != null ? Map.copyOf(domainTypes) : Map.of();
    }

    public TypedMap(Map<String, ValueType> schema, Map<String, Object> values) {
        this(schema, values, Map.of());
    }

    @Override
    public ValueType typeOf(String name) {return schema.get(name);}
}
