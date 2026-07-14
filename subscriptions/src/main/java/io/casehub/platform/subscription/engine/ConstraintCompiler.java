package io.casehub.platform.subscription.engine;

import io.casehub.platform.api.datasource.FilterExpression;
import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.platform.api.subscription.Constraint;
import io.casehub.platform.api.subscription.ConstraintOp;
import io.casehub.platform.api.subscription.SubscribableEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Predicate;

/**
 * Compiles a list of {@link Constraint}s plus tenancy/user context into a
 * {@link FilterExpression} for the alpha network.
 *
 * <p><b>Tenant isolation</b> is unconditional — injected into every FilterExpression
 * by this compiler, not user-configurable. The tenant ID is checked via instanceof
 * (native Java, MVEL-independent) and embedded in the expression string to prevent
 * cross-tenant FilterNode sharing in the alpha network.
 *
 * <p>User-defined constraints are compiled into parameterized MVEL expressions —
 * constraint values are bound as variables ({@code $p0}, {@code $p1}, ...), never
 * concatenated into the expression string. This prevents expression injection.
 */
@ApplicationScoped
public class ConstraintCompiler {

    @SuppressWarnings("unchecked")
    private static final Class<Map<String, Object>> MAP_TYPE =
            (Class<Map<String, Object>>) (Class<?>) Map.class;

    private final ExpressionEngineRegistry registry;

    @Inject
    public ConstraintCompiler(ExpressionEngineRegistry registry) {
        this.registry = registry;
    }

    private static String buildMvelExpression(final List<Constraint> constraints,
                                              final String ownerId,
                                              final Map<String, Object> variables) {
        if (constraints.isEmpty()) {
            return "true";
        }
        var joiner     = new StringJoiner(" && ");
        int paramIndex = 0;
        for (final Constraint constraint : constraints) {
            paramIndex = toMvelClause(constraint, ownerId, joiner, variables, paramIndex);
        }
        return joiner.toString();
    }

    private static int toMvelClause(final Constraint constraint, final String ownerId,
                                    final StringJoiner joiner,
                                    final Map<String, Object> variables,
                                    int paramIndex) {
        final String       field    = constraint.field();
        final String       rawValue = String.valueOf(constraint.value());
        final String       value    = "$me".equals(rawValue) ? ownerId : rawValue;
        final ConstraintOp op       = constraint.op();
        final String       param    = "$p" + paramIndex;
        variables.put(param, value);

        final String clause = switch (op) {
            case EQ -> field + " == " + param;
            case NEQ -> field + " != " + param;
            case GT -> field + " > " + param;
            case LT -> field + " < " + param;
            case GTE -> field + " >= " + param;
            case LTE -> field + " <= " + param;
            case IN -> param + ".contains(" + field + ") == true";
            case STARTS_WITH -> field + ".startsWith(" + param + ") == true";
            case CONTAINS -> field + ".indexOf(" + param + ") >= 0";
        };

        joiner.add(clause);
        return paramIndex + 1;
    }

    private static Predicate<Object> buildTenantCheck(final String tenancyId) {
        return object -> {
            if (object instanceof SubscribableEvent event) {
                return tenancyId.equals(event.tenancyId());
            }
            return false;
        };
    }

    private static Map<String, Object> extractProperties(Object obj) {
        var result = new HashMap<String, Object>();
        for (var method : obj.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {continue;}
            if (method.getDeclaringClass() == Object.class) {continue;}
            String name = method.getName();
            if (name.startsWith("get") && name.length() > 3) {
                String prop = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                try {result.put(prop, method.invoke(obj));} catch (Exception ignored) {}
            } else if (name.startsWith("is") && name.length() > 2
                       && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                String prop = Character.toLowerCase(name.charAt(2)) + name.substring(3);
                try {result.put(prop, method.invoke(obj));} catch (Exception ignored) {}
            } else if (!name.equals("hashCode") && !name.equals("toString")
                       && !name.equals("getClass") && !name.equals("notify")
                       && !name.equals("notifyAll") && !name.equals("wait")) {
                try {result.put(name, method.invoke(obj));} catch (Exception ignored) {}
            }
        }
        return result;
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
    public FilterExpression<Object> compile(final List<Constraint> constraints,
                                            final String tenancyId,
                                            final String ownerId) {
        Objects.requireNonNull(constraints, "constraints");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(ownerId, "ownerId");

        final Map<String, Object> variables      = new HashMap<>();
        final String              mvelExpression = buildMvelExpression(constraints, ownerId, variables);
        final String              expression     = "tenant=" + tenancyId + ":" + mvelExpression;

        final Predicate<Object> tenantCheck = buildTenantCheck(tenancyId);

        final Predicate<Object> predicate;
        if (constraints.isEmpty()) {
            predicate = tenantCheck;
        } else {
            final CompiledExpression<Map<String, Object>, Boolean> compiled =
                    registry.compile("mvel", mvelExpression, MAP_TYPE, Boolean.class, variables);
            predicate = obj -> {
                if (!tenantCheck.test(obj)) {return false;}
                if (obj instanceof SubscribableEvent) {
                    Map<String, Object> context = extractProperties(obj);
                    return compiled.eval(context);
                }
                return false;
            };
        }

        return new FilterExpression<>("mvel", expression, predicate);
    }
}
