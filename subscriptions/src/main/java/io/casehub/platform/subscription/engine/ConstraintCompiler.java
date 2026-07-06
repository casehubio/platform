package io.casehub.platform.subscription.engine;

import io.casehub.platform.api.datasource.FilterExpression;
import io.casehub.platform.api.subscription.Constraint;
import io.casehub.platform.api.subscription.ConstraintOp;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Predicate;

/**
 * Compiles a list of {@link Constraint}s plus tenancy/user context into a
 * {@link FilterExpression} for the alpha network.
 *
 * <p><b>Tenant isolation</b> is unconditional — injected into every FilterExpression
 * by this compiler, not user-configurable. The tenant ID is checked via MethodHandle
 * (native Java, MVEL-independent) and embedded in the expression string to prevent
 * cross-tenant FilterNode sharing in the alpha network.
 *
 * <p><b>MVEL mock phase:</b> user-defined constraint predicates always return true
 * until MVEL3 publishes. Type discrimination and tenant isolation use MethodHandle
 * and are MVEL-independent.
 */
public final class ConstraintCompiler {

    private ConstraintCompiler() {
        // Utility class — no instances
    }

    /**
     * Compiles constraints into a FilterExpression with tenant isolation.
     *
     * <p>The expression string format is {@code "tenant=<tenancyId>:<mvelExpression>"}
     * to ensure FilterNode uniqueness per tenant in the alpha network.
     *
     * @param constraints user-defined constraints (may be empty)
     * @param tenancyId   tenant for isolation (never null)
     * @param ownerId     subscription owner for {@code $me} placeholder substitution (never null)
     * @return compiled FilterExpression with tenant-aware predicate
     */
    public static FilterExpression<Object> compile(final List<Constraint> constraints,
                                                   final String tenancyId,
                                                   final String ownerId) {
        Objects.requireNonNull(constraints, "constraints");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(ownerId, "ownerId");

        final String mvelExpression = buildMvelExpression(constraints, ownerId);
        final String expression = "tenant=" + tenancyId + ":" + mvelExpression;

        // Tenant isolation via MethodHandle — MVEL-independent
        final Predicate<Object> tenantCheck = buildTenantCheck(tenancyId);

        // MVEL mock phase: user constraints always pass. Tenant check is real.
        final Predicate<Object> predicate = tenantCheck;

        return new FilterExpression<>("mvel", expression, predicate);
    }

    private static String buildMvelExpression(final List<Constraint> constraints,
                                              final String ownerId) {
        if (constraints.isEmpty()) {
            return "true";
        }
        var joiner = new StringJoiner(" && ");
        for (final Constraint constraint : constraints) {
            joiner.add(toMvelClause(constraint, ownerId));
        }
        return joiner.toString();
    }

    private static String toMvelClause(final Constraint constraint, final String ownerId) {
        final String field = constraint.field();
        final String rawValue = String.valueOf(constraint.value());
        final String value = "$me".equals(rawValue) ? ownerId : rawValue;
        final ConstraintOp op = constraint.op();

        return switch (op) {
            case EQ -> field + " == \"" + value + "\"";
            case NEQ -> field + " != \"" + value + "\"";
            case GT -> field + " > \"" + value + "\"";
            case LT -> field + " < \"" + value + "\"";
            case GTE -> field + " >= \"" + value + "\"";
            case LTE -> field + " <= \"" + value + "\"";
            case IN -> field + " IN [" + value + "]";
            case STARTS_WITH -> field + " STARTS_WITH \"" + value + "\"";
            case CONTAINS -> field + " CONTAINS \"" + value + "\"";
        };
    }

    /**
     * Builds a tenant isolation predicate using MethodHandle to call {@code tenancyId()}
     * on the event POJO. Returns false if the POJO has no tenancyId() method or the
     * tenant doesn't match.
     */
    private static Predicate<Object> buildTenantCheck(final String tenancyId) {
        return object -> {
            if (object == null) {
                return false;
            }
            try {
                var method = object.getClass().getMethod("tenancyId");
                if (method.getReturnType() != String.class) {
                    return false;
                }
                var handle = MethodHandles.lookup().unreflect(method);
                final String pojoTenant = (String) handle.invoke(object);
                return tenancyId.equals(pojoTenant);
            } catch (Throwable e) {
                return false;
            }
        };
    }
}
