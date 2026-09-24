package io.casehub.yaml.core.type;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TypedVariables {

    private TypedVariables() {}

    public static TypedMap parse(Map<String, Object> rawVariables) {
        var schema = new LinkedHashMap<String, ValueType>();
        var values = new LinkedHashMap<String, Object>();
        for (var entry : rawVariables.entrySet()) {
            TypedName tn = TypedName.parse(entry.getKey());
            schema.put(tn.name(), tn.type());
            try {
                values.put(tn.name(), tn.type().parse(String.valueOf(entry.getValue())));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    "Variable '" + tn.name() + "' (" + tn.type()
                    + "): invalid value '" + entry.getValue() + "'", e);
            }
        }
        return new TypedMap(Map.copyOf(schema), Map.copyOf(values));
    }
}
