package io.casehub.platform.api.subscription;

import java.util.Objects;

/**
 * Filter constraint for subscription matching. Compares an event property
 * against an expected value using the specified operator.
 *
 * @param field event property name (e.g., "status", "assignee", "priority")
 * @param op    comparison operator
 * @param value expected value as String (MVEL coerces to POJO field type at evaluation)
 */
public record Constraint(
        String field,
        ConstraintOp op,
        String value
) {
    public Constraint {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(op, "op");
        Objects.requireNonNull(value, "value");
    }
}
