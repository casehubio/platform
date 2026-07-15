package io.casehub.platform.subscription.rest;

import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.platform.api.expression.JQExpressionEvaluator;
import io.casehub.platform.api.expression.MvelExpressionEvaluator;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.subscription.NotificationTarget;
import io.casehub.platform.api.subscription.ReactiveSubscriptionStore;
import io.casehub.platform.api.subscription.SubscriptionConstants;
import io.casehub.platform.api.subscription.SubscriptionInput;
import io.casehub.platform.api.subscription.SubscriptionPage;
import io.casehub.platform.api.subscription.SubscriptionQuery;
import io.casehub.platform.api.subscription.SubscriptionScope;
import io.casehub.platform.api.subscription.SubscriptionUpdate;
import io.casehub.platform.api.subscription.TargetType;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * REST endpoints for subscription CRUD operations.
 *
 * <p>All endpoints use {@link CurrentPrincipal} for identity — ownerId and tenancyId
 * are never passed as request parameters. Tenant and user isolation is enforced by
 * the principal.
 *
 * <p>All methods return {@code Uni<>} — RESTEasy Reactive on the event loop.
 */
@ApplicationScoped
@Path("/subscriptions")
public class SubscriptionResource {

    private final ReactiveSubscriptionStore store;
    private final CurrentPrincipal          principal;
    private final ExpressionEngineRegistry  expressionRegistry;

    @Inject
    public SubscriptionResource(final ReactiveSubscriptionStore store,
                                final CurrentPrincipal principal,
                                final ExpressionEngineRegistry expressionRegistry) {
        this.store              = store;
        this.principal          = principal;
        this.expressionRegistry = expressionRegistry;
    }

    @POST
    public Uni<Response> create(final SubscriptionInput input) {
        var effectiveScope = input.scope();

        if (effectiveScope == SubscriptionScope.SYSTEM) {
            if (!principal.hasGroup(SubscriptionConstants.SYSTEM_SUBSCRIPTION_ADMIN_GROUP)) {
                return Uni.createFrom().item(Response.status(403).build());
            }
            if (input.targets() == null || input.targets().isEmpty()) {
                return Uni.createFrom().item(Response.status(400)
                                                     .entity("SYSTEM scope requires explicit targets").build());
            }
            for (var filter : input.filters()) {
                String expr = extractExpression(filter);
                if (expr.contains("$me")) {
                    return Uni.createFrom().item(Response.status(400)
                                                         .entity("$me filter not allowed for SYSTEM scope").build());
                }
            }
        }

        final var targets = (effectiveScope != SubscriptionScope.SYSTEM
                             && (input.targets() == null || input.targets().isEmpty()))
                            ? List.of(new NotificationTarget(TargetType.USER, principal.actorId()))
                            : input.targets();

        final var securedInput = new SubscriptionInput(
                principal.actorId(),
                principal.tenancyId(),
                input.name(),
                input.eventType(),
                input.filters(),
                targets,
                input.includeActor(),
                input.template(),
                input.enabled(),
                effectiveScope
        );
        for (var filter : securedInput.filters()) {
            try {
                expressionRegistry.validate(filter.type(), extractExpression(filter));
            } catch (Exception e) {
                return Uni.createFrom().item(Response.status(400)
                        .entity("Invalid filter expression: " + e.getMessage()).build());
            }
        }

        return store.store(securedInput)
                    .map(s -> Response.status(201).entity(s).build());
    }

    @GET
    public Uni<SubscriptionPage> list(
            @QueryParam("enabled") final Boolean enabled,
            @QueryParam("scope") final SubscriptionScope scope,
            @QueryParam("cursor") final String cursor,
            @QueryParam("limit") @DefaultValue("25") final int limit) {
        return store.find(new SubscriptionQuery(
                scope == SubscriptionScope.SYSTEM ? null : principal.actorId(),
                principal.tenancyId(),
                scope,
                enabled,
                cursor,
                limit
        ));
    }

    /**
     * Get a subscription by id.
     *
     * @param id subscription id
     * @return 200 with subscription, or 404 if not found/wrong tenant/wrong user
     */
    @GET
    @Path("/{id}")
    public Uni<Response> getById(@PathParam("id") final String id) {
        return store.findById(id, principal.actorId(), principal.tenancyId())
                    .map(opt -> opt.map(s -> Response.ok(s).build())
                                   .orElse(Response.status(404).build()));
    }

    @PATCH
    @Path("/{id}")
    public Uni<Response> update(@PathParam("id") final String id, final SubscriptionUpdate update) {
        return store.findById(id, principal.actorId(), principal.tenancyId())
                    .chain(opt -> {
                        if (opt.isEmpty()) {
                            return Uni.createFrom().item(Response.status(404).build());
                        }
                        if (isUnauthorizedSystemAccess(opt.get().scope())) {
                            return Uni.createFrom().item(Response.status(403).build());
                        }
                        return store.update(id, principal.actorId(), principal.tenancyId(), update)
                                    .map(result -> result.map(s -> Response.ok(s).build())
                                                         .orElse(Response.status(404).build()));
                    });
    }

    @DELETE
    @Path("/{id}")
    public Uni<Response> delete(@PathParam("id") final String id) {
        return store.findById(id, principal.actorId(), principal.tenancyId())
                    .chain(opt -> {
                        if (opt.isEmpty()) {
                            return Uni.createFrom().item(Response.status(404).build());
                        }
                        if (isUnauthorizedSystemAccess(opt.get().scope())) {
                            return Uni.createFrom().item(Response.status(403).build());
                        }
                        return store.delete(id, principal.actorId(), principal.tenancyId())
                                    .map(deleted -> deleted
                                                    ? Response.noContent().build()
                                                    : Response.status(404).build());
                    });
    }

    @PATCH
    @Path("/{id}/enable")
    public Uni<Response> enable(@PathParam("id") final String id) {
        return store.findById(id, principal.actorId(), principal.tenancyId())
                    .chain(opt -> {
                        if (opt.isEmpty()) {
                            return Uni.createFrom().item(Response.status(404).build());
                        }
                        if (isUnauthorizedSystemAccess(opt.get().scope())) {
                            return Uni.createFrom().item(Response.status(403).build());
                        }
                        return store.update(id, principal.actorId(), principal.tenancyId(),
                                            new SubscriptionUpdate(null, null, null, null, null, null, true))
                                    .map(result -> result.map(s -> Response.ok(s).build())
                                                         .orElse(Response.status(404).build()));
                    });
    }

    @PATCH
    @Path("/{id}/disable")
    public Uni<Response> disable(@PathParam("id") final String id) {
        return store.findById(id, principal.actorId(), principal.tenancyId())
                    .chain(opt -> {
                        if (opt.isEmpty()) {
                            return Uni.createFrom().item(Response.status(404).build());
                        }
                        if (isUnauthorizedSystemAccess(opt.get().scope())) {
                            return Uni.createFrom().item(Response.status(403).build());
                        }
                        return store.update(id, principal.actorId(), principal.tenancyId(),
                                            new SubscriptionUpdate(null, null, null, null, null, null, false))
                                    .map(result -> result.map(s -> Response.ok(s).build())
                                                         .orElse(Response.status(404).build()));
                    });
    }

    private boolean isUnauthorizedSystemAccess(SubscriptionScope scope) {
        return scope == SubscriptionScope.SYSTEM
               && !principal.hasGroup(SubscriptionConstants.SYSTEM_SUBSCRIPTION_ADMIN_GROUP);
    }

    private static String extractExpression(io.casehub.platform.api.expression.ExpressionEvaluator evaluator) {
        if (evaluator instanceof io.casehub.platform.api.expression.MvelExpressionEvaluator m) {return m.expression();}
        if (evaluator instanceof io.casehub.platform.api.expression.JQExpressionEvaluator j) {return j.expression();}
        throw new IllegalArgumentException("Unknown evaluator type: " + evaluator.type());
    }
}
