package io.casehub.extraction;

import java.util.Objects;
import java.util.Set;

public sealed interface VariableConstraint {

    boolean required();

    VariableConstraint optional();

    record StringConstraint(boolean required) implements VariableConstraint {
        @Override
        public VariableConstraint optional() { return new StringConstraint(false); }
    }

    record EnumConstraint(Set<String> values, boolean required) implements VariableConstraint {
        public EnumConstraint {
            Objects.requireNonNull(values, "values");
            if (values.isEmpty()) throw new IllegalArgumentException("Enum constraint must have at least one value");
            values = Set.copyOf(values);
        }

        @Override
        public VariableConstraint optional() { return new EnumConstraint(values, false); }
    }

    static VariableConstraint string() { return new StringConstraint(true); }

    static VariableConstraint enumOf(String... values) {
        Objects.requireNonNull(values, "values");
        return new EnumConstraint(Set.of(values), true);
    }
}
