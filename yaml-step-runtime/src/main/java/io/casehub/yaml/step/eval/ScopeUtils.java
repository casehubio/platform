package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.resolver.ObjectVariableSource;
import io.casehub.yaml.core.resolver.VariableResolver;

import java.util.Map;

final class ScopeUtils {

    private ScopeUtils() {}

    @SuppressWarnings("unchecked")
    static VariableResolver pushScope(VariableResolver resolver, String prefix, Object value) {
        VariableResolver result = resolver.withObjectScope(prefix, drillSource(value));
        if (value instanceof Map<?, ?> map) {
            result = result.withExpressionContext(prefix, (Map<String, Object>) map);
        }
        return result;
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
