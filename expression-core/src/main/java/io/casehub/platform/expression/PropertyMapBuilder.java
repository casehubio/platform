package io.casehub.platform.expression;

import java.util.HashMap;
import java.util.Map;

public final class PropertyMapBuilder {
    private PropertyMapBuilder() {}

    @SuppressWarnings("unchecked")
    public static void put(Map<String, Object> map, String key, String value) {
        int dot = key.indexOf('.');
        if (dot == -1) { map.put(key, value); return; }
        String head = key.substring(0, dot);
        String tail = key.substring(dot + 1);
        Map<String, Object> nested = (Map<String, Object>)
                map.computeIfAbsent(head, k -> new HashMap<>());
        put(nested, tail, value);
    }
}
