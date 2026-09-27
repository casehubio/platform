package io.casehub.yaml.core.step;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public sealed interface MatchPattern {

    boolean matches(Object scrutinee);

    record ValuePattern(Object value) implements MatchPattern {
        @Override
        public boolean matches(Object scrutinee) {
            return Objects.equals(value, scrutinee);
        }
    }

    record StructuralPattern(Map<String, Object> fields) implements MatchPattern {
        public StructuralPattern {
            fields = Map.copyOf(fields);
        }

        @Override
        public boolean matches(Object scrutinee) {
            if (!(scrutinee instanceof Map<?, ?> map)) {return false;}
            for (var entry : fields.entrySet()) {
                if (!Objects.equals(map.get(entry.getKey()), entry.getValue())) {return false;}
            }
            return true;
        }
    }

    record AnyOfPattern(List<Object> values) implements MatchPattern {
        public AnyOfPattern {
            values = List.copyOf(values);
        }

        @Override
        public boolean matches(Object scrutinee) {
            for (Object v : values) {
                if (Objects.equals(v, scrutinee)) {return true;}
            }
            return false;
        }
    }

    record DefaultPattern() implements MatchPattern {
        @Override
        public boolean matches(Object scrutinee) {
            return true;
        }
    }
}
