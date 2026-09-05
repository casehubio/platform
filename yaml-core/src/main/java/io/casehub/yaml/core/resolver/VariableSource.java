package io.casehub.yaml.core.resolver;

@FunctionalInterface
public interface VariableSource {

    String resolve(String name);

    static VariableSource chain(VariableSource... sources) {
        return name -> {
            for (VariableSource source : sources) {
                String value = source.resolve(name);
                if (value != null) return value;
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
            String fieldPath = name.substring(dot + 1);
            Object current   = entry;
            for (String part : fieldPath.split("\\.")) {
                if (current instanceof java.util.Map<?, ?> m) {
                    current = m.get(part);
                } else {
                    return "";
                }
            }
            return current != null ? current.toString() : "";
        };
    }

}
