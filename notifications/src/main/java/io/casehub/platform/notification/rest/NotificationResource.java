package io.casehub.platform.notification.rest;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.notification.Notification;
import io.casehub.platform.api.notification.NotificationPage;
import io.casehub.platform.api.notification.NotificationQuery;
import io.casehub.platform.api.notification.NotificationStatus;
import io.casehub.platform.api.notification.ReactiveNotificationStore;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.Optional;

/**
 * REST endpoints for notification retrieval and management.
 *
 * <p>All endpoints use {@link CurrentPrincipal} for identity — userId and tenancyId
 * are never passed as request parameters. Tenant isolation is enforced by the principal.
 *
 * <p>All methods return {@code Uni<>} — RESTEasy Reactive on the event loop.
 */
@ApplicationScoped
@Path("/notifications")
public class NotificationResource {

    private final ReactiveNotificationStore store;
    private final CurrentPrincipal principal;

    @Inject
    public NotificationResource(ReactiveNotificationStore store, CurrentPrincipal principal) {
        this.store = store;
        this.principal = principal;
    }

    /**
     * List notifications for the current user.
     *
     * @param status filter by status (optional)
     * @param category filter by category (optional)
     * @param cursor pagination cursor (optional)
     * @param limit max results per page (default 25)
     */
    @GET
    public Uni<NotificationPage> list(
            @QueryParam("status") NotificationStatus status,
            @QueryParam("category") String category,
            @QueryParam("cursor") String cursor,
            @QueryParam("limit") Integer limit) {

        var query = new NotificationQuery(
            principal.actorId(),
            principal.tenancyId(),
            status,
            category,
            cursor,
            limit != null ? limit : 25
        );

        return store.find(query);
    }

    /**
     * Get unread notification count for the current user.
     */
    @GET
    @Path("/unread-count")
    public Uni<Map<String, Long>> unreadCount() {
        return store.unreadCount(principal.actorId(), principal.tenancyId())
            .map(count -> Map.of("count", count));
    }

    /**
     * Mark a notification as read.
     *
     * @return 200 with updated notification, or 404 if not found/wrong tenant/wrong user
     */
    @PATCH
    @Path("/{id}/read")
    public Uni<Response> markRead(@PathParam("id") String id) {
        return store.markRead(id, principal.actorId(), principal.tenancyId())
            .map(opt -> opt
                .map(notification -> Response.ok(notification).build())
                .orElse(Response.status(404).build()));
    }

    /**
     * Dismiss a notification.
     *
     * @return 200 with updated notification, or 404 if not found/wrong tenant/wrong user
     */
    @PATCH
    @Path("/{id}/dismiss")
    public Uni<Response> dismiss(@PathParam("id") String id) {
        return store.dismiss(id, principal.actorId(), principal.tenancyId())
            .map(opt -> opt
                .map(notification -> Response.ok(notification).build())
                .orElse(Response.status(404).build()));
    }

    /**
     * Mark all unread notifications as read for the current user.
     *
     * @return count of notifications marked as read
     */
    @POST
    @Path("/mark-all-read")
    public Uni<Map<String, Integer>> markAllRead() {
        return store.markAllRead(principal.actorId(), principal.tenancyId())
            .map(count -> Map.of("count", count));
    }
}
