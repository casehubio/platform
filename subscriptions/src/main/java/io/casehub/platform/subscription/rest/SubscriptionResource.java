package io.casehub.platform.subscription.rest;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.subscription.NotificationTarget;
import io.casehub.platform.api.subscription.ReactiveSubscriptionStore;
import io.casehub.platform.api.subscription.SubscriptionInput;
import io.casehub.platform.api.subscription.SubscriptionPage;
import io.casehub.platform.api.subscription.SubscriptionQuery;
import io.casehub.platform.api.subscription.SubscriptionUpdate;
import io.casehub.platform.api.subscription.TargetType;

import java.util.List;
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
    private final CurrentPrincipal principal;

    @Inject
    public SubscriptionResource(final ReactiveSubscriptionStore store, final CurrentPrincipal principal) {
        this.store = store;
        this.principal = principal;
    }

    /**
     * Create a new subscription.
     *
     * <p>The ownerId and tenancyId in the request body are ignored — they are overridden
     * from the current principal to enforce user-level authorization. If targets is null
     * or empty, defaults to [NotificationTarget(USER, principal.actorId())].
     *
     * @param input subscription input (ownerId/tenancyId overridden from principal)
     * @return 201 with created subscription
     */
    @POST
    public Uni<Response> create(final SubscriptionInput input) {
        // Default targets to USER pointing to owner if not provided
        final var targets = (input.targets() == null || input.targets().isEmpty())
            ? List.of(new NotificationTarget(TargetType.USER, principal.actorId()))
            : input.targets();

        final var securedInput = new SubscriptionInput(
            principal.actorId(),
            principal.tenancyId(),
            input.name(),
            input.eventType(),
            input.constraints(),
            targets,
            input.includeActor(),
            input.template(),
            input.enabled()
        );
        return store.store(securedInput)
            .map(s -> Response.status(201).entity(s).build());
    }

    /**
     * List subscriptions for the current user.
     *
     * @param enabled filter by enabled state (optional)
     * @param cursor pagination cursor (optional)
     * @param limit max results per page (default 25)
     * @return page of subscriptions with optional next cursor
     */
    @GET
    public Uni<SubscriptionPage> list(
            @QueryParam("enabled") final Boolean enabled,
            @QueryParam("cursor") final String cursor,
            @QueryParam("limit") @DefaultValue("25") final int limit) {
        final var query = new SubscriptionQuery(
            principal.actorId(),
            principal.tenancyId(),
            enabled,
            cursor,
            limit
        );
        return store.find(query);
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

    /**
     * Update a subscription with partial changes.
     *
     * <p>Only non-null fields in the update are applied.
     *
     * @param id subscription id
     * @param update partial update
     * @return 200 with updated subscription, or 404 if not found/wrong tenant/wrong user
     */
    @PATCH
    @Path("/{id}")
    public Uni<Response> update(@PathParam("id") final String id, final SubscriptionUpdate update) {
        return store.update(id, principal.actorId(), principal.tenancyId(), update)
            .map(opt -> opt.map(s -> Response.ok(s).build())
                .orElse(Response.status(404).build()));
    }

    /**
     * Delete a subscription.
     *
     * @param id subscription id
     * @return 204 if deleted, or 404 if not found/wrong tenant/wrong user
     */
    @DELETE
    @Path("/{id}")
    public Uni<Response> delete(@PathParam("id") final String id) {
        return store.delete(id, principal.actorId(), principal.tenancyId())
            .map(deleted -> deleted
                ? Response.noContent().build()
                : Response.status(404).build());
    }

    /**
     * Enable a subscription.
     *
     * @param id subscription id
     * @return 200 with updated subscription, or 404 if not found/wrong tenant/wrong user
     */
    @PATCH
    @Path("/{id}/enable")
    public Uni<Response> enable(@PathParam("id") final String id) {
        return store.update(id, principal.actorId(), principal.tenancyId(),
                new SubscriptionUpdate(null, null, null, null, null, null, true))
            .map(opt -> opt.map(s -> Response.ok(s).build())
                .orElse(Response.status(404).build()));
    }

    /**
     * Disable a subscription.
     *
     * @param id subscription id
     * @return 200 with updated subscription, or 404 if not found/wrong tenant/wrong user
     */
    @PATCH
    @Path("/{id}/disable")
    public Uni<Response> disable(@PathParam("id") final String id) {
        return store.update(id, principal.actorId(), principal.tenancyId(),
                new SubscriptionUpdate(null, null, null, null, null, null, false))
            .map(opt -> opt.map(s -> Response.ok(s).build())
                .orElse(Response.status(404).build()));
    }
}
