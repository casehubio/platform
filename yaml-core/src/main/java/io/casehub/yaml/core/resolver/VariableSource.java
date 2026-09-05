package io.casehub.yaml.core.resolver;

@FunctionalInterface
public interface VariableSource {

    String resolve(String name);

    static VariableSource chain(VariableSource... sources) {
        return name -> {
            for (VariableSource source : sources) {
                String value = source.resolve(name);
                if (value != null) {return value;}
            }
            return null;
        };
    }

    static VariableSource env() {
        return System::getenv;
    }

    static VariableSource systemProperty() {
        return System::getProperty;
    }

    @SuppressWarnings("unchecked")
    static VariableSource nested(java.util.Map<String, java.util.Map<String, Object>> data) {
        return name -> {
            int                           dot   = name.indexOf('.');
            String                        key   = dot >= 0 ? name.substring(0, dot) : name;
            java.util.Map<String, Object> entry = data.get(key);
            if (entry == null) {return null;}
            if (dot < 0) {return entry.toString();}
            Object value = drillFields(entry, name.substring(dot + 1));
            return value != null ? value.toString() : null;
        };
    }

    static VariableSource forEachContext(java.util.Map<String, String> simple,
                                         java.util.Map<String, java.util.Map<String, Object>> rows) {
        return name -> {
            int dot = name.indexOf('.');
            if (dot >= 0 && rows != null) {
                String                        rowName = name.substring(0, dot);
                java.util.Map<String, Object> row     = rows.get(rowName);
                if (row != null) {
                    String fieldPath = name.substring(dot + 1);
                    Object value     = drillFields(row, fieldPath);
                    if (value != null) {return value.toString();}
                    throw new IllegalArgumentException(
                            "Field '" + fieldPath + "' not found in '" + rowName
                            + "'. Available: " + row.keySet());
                }
            }
            if (simple != null) {
                String value = simple.get(name);
                if (value != null) {return value;}
            }
            if (rows != null && rows.containsKey(name)) {
                throw new IllegalArgumentException(
                        "'" + name + "' is a row — use field access like ${each."
                        + name + ".fieldName}. Available: " + rows.get(name).keySet());
            }
            return null;
        };
    }

    static VariableSource lenient(VariableSource delegate) {
        return name -> {
            String result = delegate.resolve(name);
            return result != null ? result : "";
        };
    }

    @SuppressWarnings("unchecked")
    private static Object drillFields(java.util.Map<String, Object> map, String dotPath) {
        Object current = map;
        for (String part : dotPath.split("\\.")) {
            if (current instanceof java.util.Map<?, ?> m) {
                current = m.get(part);
            } else {
                return null;
            }
        }
        return current;
    }
}
