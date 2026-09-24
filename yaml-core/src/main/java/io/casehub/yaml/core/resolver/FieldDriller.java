package io.casehub.yaml.core.resolver;

import java.util.Map;

final class FieldDriller {
    private FieldDriller() {}

    @SuppressWarnings("unchecked")
    static Object drill(Map<String, Object> map, String dotPath) {
        Object current = map;
        for (String part : dotPath.split("\\.")) {
            if (current instanceof Map<?, ?> m) {
                current = m.get(part);
            } else {
                return null;
            }
        }
        return current;
    }
}
