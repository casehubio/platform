package io.casehub.yaml.step.util;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Records {

    private Records() {}

    public static Map<String, Object> toMap(Object record) {return toMap(record, false);}

    public static Map<String, Object> toMapNonNull(Object record) {return toMap(record, true);}

    private static Map<String, Object> toMap(Object record, boolean omitNulls) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        if (!record.getClass().isRecord()) {
            throw new IllegalArgumentException(
                    record.getClass().getName() + " is not a record");
        }
        var result = new java.util.LinkedHashMap<String, Object>();
        for (java.lang.reflect.RecordComponent component : record.getClass().getRecordComponents()) {
            try {
                Object value     = component.getAccessor().invoke(record);
                Object converted = convert(value, omitNulls);
                if (!omitNulls || converted != null) {
                    result.put(component.getName(), converted);
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(
                        "Failed to read record component: " + component.getName(), e);
            }
        }
        return result;
    }


    private static Object convert(Object value, boolean omitNulls) {
        if (value == null) {
            return null;
        }
        if (value instanceof Optional<?> opt) {
            return opt.orElse(null);
        }
        if (value.getClass().isRecord()) {
            return toMap(value, omitNulls);
        }
        if (value instanceof List<?> list && !list.isEmpty()
            && list.getFirst().getClass().isRecord()) {
            return list.stream()
                       .map(item -> item.getClass().isRecord() ? toMap(item, omitNulls) : item)
                       .toList();
        }
        return value;
    }
}
