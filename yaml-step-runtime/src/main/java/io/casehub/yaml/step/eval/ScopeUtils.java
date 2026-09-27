package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.resolver.ObjectVariableSource;
import io.casehub.yaml.core.resolver.VariableResolver;

import java.util.Map;

final class ScopeUtils {

    private ScopeUtils() {}

    static VariableResolver pushScope(VariableResolver resolver, String prefix, Object value) {
        return resolver.withObjectScope(prefix, drillSource(value));
    }

    static ObjectVariableSource drillSource(Object value) {
        if (value instanceof Map<?, ?> map) {
            return name -> {
                if (name.isEmpty()) return value;
                Object direct = map.get(name);
                if (direct != null) return direct;
                int dot = name.indexOf('.');
                if (dot > 0) {
                    Object root = map.get(name.substring(0, dot));
                    if (root instanceof Map<?, ?> nested) {
                        return nested.get(name.substring(dot + 1));
                    }
                }
                return null;
            };
        }
        return name -> value;
    }
}
