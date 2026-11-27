package io.casehub.template.registry.mf2;

import java.util.LinkedHashMap;
import java.util.Map;

final class Mf2TypeCoercer {

    private Mf2TypeCoercer() {}

    static Map<String, Object> coerce(Map<String, String> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : values.entrySet()) {
            result.put(entry.getKey(), coerceValue(entry.getValue()));
        }
        return result;
    }

    private static Object coerceValue(String value) {
        if (value == null) return null;
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }
}
