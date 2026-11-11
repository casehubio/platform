package io.casehub.yaml.step.util;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Records {

    private Records() {}

    public static Map<String, Object> toMap(Object record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        if (!record.getClass().isRecord()) {
            throw new IllegalArgumentException(
                    record.getClass().getName() + " is not a record");
        }
        var result = new LinkedHashMap<String, Object>();
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            try {
                Object value = component.getAccessor().invoke(record);
                result.put(component.getName(), convert(value));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(
                        "Failed to read record component: " + component.getName(), e);
            }
        }
        return result;
    }

    private static Object convert(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Optional<?> opt) {
            return opt.orElse(null);
        }
        if (value.getClass().isRecord()) {
            return toMap(value);
        }
        if (value instanceof List<?> list && !list.isEmpty()
                && list.getFirst().getClass().isRecord()) {
            return list.stream()
                    .map(item -> item.getClass().isRecord() ? toMap(item) : item)
                    .toList();
        }
        return value;
    }
}
