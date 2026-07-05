package io.casehub.platform.api.subscription;

/**
 * Constraint operators for filtering event properties in subscriptions.
 */
public enum ConstraintOp {
    /**
     * Equality check.
     */
    EQ,

    /**
     * Inequality check.
     */
    NEQ,

    /**
     * Greater than.
     */
    GT,

    /**
     * Less than.
     */
    LT,

    /**
     * Greater than or equal.
     */
    GTE,

    /**
     * Less than or equal.
     */
    LTE,

    /**
     * Membership check — value is in a collection.
     */
    IN,

    /**
     * String prefix match.
     */
    STARTS_WITH,

    /**
     * String substring match.
     */
    CONTAINS
}
