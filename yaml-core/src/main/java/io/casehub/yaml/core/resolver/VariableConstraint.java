package io.casehub.yaml.core.resolver;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public sealed interface VariableConstraint {

    boolean required();

    Map<String, String> metadata();

    VariableConstraint optional();

    VariableConstraint withMetadata(Map<String, String> metadata);

    record StringConstraint(boolean required, Map<String, String> metadata) implements VariableConstraint {
        public StringConstraint {
            metadata = metadata != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(metadata))
                : Map.of();
        }

        public StringConstraint(boolean required) { this(required, Map.of()); }

        @Override
        public VariableConstraint optional() { return new StringConstraint(false, metadata); }

        @Override
        public VariableConstraint withMetadata(Map<String, String> metadata) {
            return new StringConstraint(required, metadata);
        }
    }

    record EnumConstraint(Set<String> values, boolean required, Map<String, String> metadata) implements VariableConstraint {
        public EnumConstraint {
            Objects.requireNonNull(values, "values");
            if (values.isEmpty()) throw new IllegalArgumentException("Enum constraint must have at least one value");
            values = Set.copyOf(values);
            metadata = metadata != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(metadata))
                : Map.of();
        }

        public EnumConstraint(Set<String> values, boolean required) { this(values, required, Map.of()); }

        @Override
        public VariableConstraint optional() { return new EnumConstraint(values, false, metadata); }

        @Override
        public VariableConstraint withMetadata(Map<String, String> metadata) {
            return new EnumConstraint(values, required, metadata);
        }
    }

    static VariableConstraint string() { return new StringConstraint(true); }

    static VariableConstraint enumOf(String... values) {
        Objects.requireNonNull(values, "values");
        return new EnumConstraint(Set.of(values), true);
    }
}
