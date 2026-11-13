package io.casehub.yaml.core.resolver;

import java.util.Map;
import java.util.List;

public final class FieldDriller {
    private FieldDriller() {}

    @SuppressWarnings("unchecked")
    public static Object drill(Object value, String dotPath) {
        Object current = value;
        for (String part : dotPath.replaceAll("\\[(\\d+)\\]", ".$1").split("\\.")) {
            if (current instanceof Map<?, ?> m) {
                current = m.get(part);
            } else if (current instanceof List<?> list) {
                try {
                    int index = Integer.parseInt(part);
                    if (index < 0 || index >= list.size()) return null;
                    current = list.get(index);
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return current;
    }
}
