package io.casehub.platform.api.expression;

public enum ExpressionContext {
    /** Boolean predicate evaluation. Platform default: mvel. */
    CONDITION,
    /** Data transformation. Platform default: jq. */
    TRANSFORM,
    /** Data filtering. Platform default: jq. */
    FILTER,
    /** Inline parameter expression resolution. Platform default: mvel. */
    PARAMETER
}
