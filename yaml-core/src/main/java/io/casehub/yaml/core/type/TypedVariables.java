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
            TypedName tn              = TypedName.parse(entry.getKey());
            Object    raw             = entry.getValue();
            boolean   hasExplicitType = entry.getKey().indexOf(':') >= 0;

            if (tn.isScalar()) {
                ValueType effectiveType = tn.scalarType();
                if (!hasExplicitType && raw != null) {
                    effectiveType = inferType(raw);
                }
                schema.put(tn.name(), effectiveType);
                if (raw == null || effectiveType.validate(raw)) {
                    values.put(tn.name(), raw);
                } else {
                    try {
                        values.put(tn.name(), effectiveType.parse(String.valueOf(raw)));
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException(
                                "Variable '" + tn.name() + "' (" + effectiveType
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

    private static ValueType inferType(Object value) {
        if (value instanceof Integer || value instanceof Long) {return ValueType.INTEGER;}
        if (value instanceof Double || value instanceof Float) {return ValueType.NUMBER;}
        if (value instanceof Boolean) {return ValueType.BOOLEAN;}
        return ValueType.STRING;
    }

}
