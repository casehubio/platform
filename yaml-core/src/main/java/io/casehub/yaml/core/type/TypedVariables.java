package io.casehub.yaml.core.type;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TypedVariables {

    private TypedVariables() {}

    public static TypedMap parse(Map<String, Object> rawVariables) {
        var schema      = new LinkedHashMap<String, ValueType>();
        var domainTypes = new LinkedHashMap<String, String>();
        var values      = new LinkedHashMap<String, Object>();
        for (var entry : rawVariables.entrySet()) {
            TypedName tn  = TypedName.parse(entry.getKey());
            Object    raw = entry.getValue();
            if (tn.isScalar()) {
                schema.put(tn.name(), tn.scalarType());
                if (raw == null || tn.scalarType().validate(raw)) {
                    values.put(tn.name(), raw);
                } else {
                    try {
                        values.put(tn.name(), tn.scalarType().parse(String.valueOf(raw)));
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException(
                                "Variable '" + tn.name() + "' (" + tn.scalarType()
                                + "): invalid value '" + raw + "'", e);
                    }
                }
            } else {
                domainTypes.put(tn.name(), tn.typeName());
                values.put(tn.name(), raw);
            }
        }
        return new TypedMap(schema, values, domainTypes);
    }
}
