package io.casehub.platform.api.subscription;

import java.util.Objects;

public record Constraint(
        String field,
        ConstraintOp op,
        String value
) {
    private static final java.util.regex.Pattern FIELD_PATTERN =
            java.util.regex.Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*");

    public Constraint {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(op, "op");
        Objects.requireNonNull(value, "value");
        if (!FIELD_PATTERN.matcher(field).matches()) {
            throw new IllegalArgumentException(
                    "Invalid constraint field name: '" + field
                    + "' — must be identifier-dot-separated path (e.g., 'status', 'assignee.name')");
        }
    }
}
