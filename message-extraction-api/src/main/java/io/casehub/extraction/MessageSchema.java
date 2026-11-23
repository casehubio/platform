package io.casehub.extraction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record MessageSchema(List<MessageType> types) {
    public MessageSchema {
        Objects.requireNonNull(types, "types");
        if (types.isEmpty()) throw new IllegalArgumentException("Schema must have at least one message type");
        types = List.copyOf(types);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<MessageType> types = new ArrayList<>();

        public TypeBuilder message(String name) { return new TypeBuilder(this, name); }

        public MessageSchema build() { return new MessageSchema(List.copyOf(types)); }

        private void addType(MessageType type) { types.add(type); }
    }

    public static final class TypeBuilder {
        private final Builder parent;
        private final String name;
        private final List<String> templates = new ArrayList<>();
        private final Map<String, VariableConstraint> variables = new LinkedHashMap<>();

        TypeBuilder(Builder parent, String name) {
            this.parent = Objects.requireNonNull(parent);
            this.name = Objects.requireNonNull(name);
        }

        public TypeBuilder template(String template) {
            templates.add(Objects.requireNonNull(template, "template"));
            return this;
        }

        public TypeBuilder variable(String name, VariableConstraint constraint) {
            variables.put(Objects.requireNonNull(name), Objects.requireNonNull(constraint));
            return this;
        }

        public Builder done() {
            parent.addType(new MessageType(name, templates, variables));
            return parent;
        }
    }
}
