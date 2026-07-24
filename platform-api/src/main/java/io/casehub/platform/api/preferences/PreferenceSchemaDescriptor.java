package io.casehub.platform.api.preferences;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PreferenceSchemaDescriptor(
        String namespace,
        String name,
        String qualifiedName,
        String type,
        String label,
        String description,
        String defaultValue,
        boolean multiValue,
        Map<String, Object> constraints,
        List<EnumOption> options
) {
    public PreferenceSchemaDescriptor {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(qualifiedName, "qualifiedName");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(defaultValue, "defaultValue");
        constraints = constraints == null ? Map.of() : Map.copyOf(constraints);
        options = options == null ? List.of() : List.copyOf(options);
    }

    public static Builder of(PreferenceKey<?> key) {
        return new Builder(key);
    }

    public static final class Builder {
        private final String namespace;
        private final String name;
        private final String qualifiedName;
        private final String defaultValue;
        private final boolean multiValue;
        private String type;
        private String label;
        private String description;
        private Map<String, Object> constraints;
        private List<EnumOption> options;

        private Builder(PreferenceKey<?> key) {
            this.namespace = key.namespace();
            this.name = key.name();
            this.qualifiedName = key.qualifiedName();
            this.defaultValue = key.defaultValue().toSerializedValue();
            this.multiValue = key.defaultValue() instanceof MultiValuePreference;
            this.type = inferType(key.defaultValue());
            this.label = key.name();
        }

        public Builder type(String type) { this.type = type; return this; }
        public Builder label(String label) { this.label = label; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder constraints(Map<String, Object> constraints) { this.constraints = constraints; return this; }
        public Builder options(List<EnumOption> options) { this.options = options; return this; }

        public PreferenceSchemaDescriptor build() {
            return new PreferenceSchemaDescriptor(
                    namespace, name, qualifiedName, type, label,
                    description, defaultValue, multiValue, constraints, options);
        }

        private static String inferType(Preference defaultValue) {
            if (defaultValue instanceof IntPreference) return "integer";
            if (defaultValue instanceof DoublePreference) return "number";
            if (defaultValue instanceof BooleanPreference) return "boolean";
            if (defaultValue instanceof DurationPreference) return "duration";
            return "string";
        }
    }
}
