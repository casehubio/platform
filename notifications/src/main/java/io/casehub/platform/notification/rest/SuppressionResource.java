package io.casehub.platform.notification.rest;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.notification.settings.MuteRule;
import io.casehub.platform.api.notification.settings.MuteRuleInput;
import io.casehub.platform.api.notification.settings.Snooze;
import io.casehub.platform.api.notification.settings.SnoozeInput;
import io.casehub.platform.api.notification.settings.SuppressionStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * REST endpoints for notification suppression — mute rules and snooze.
 *
 * <p>All endpoints use {@link CurrentPrincipal} for identity — userId and tenancyId
 * are never passed as request parameters. Tenant isolation is enforced by the principal.
 */
@ApplicationScoped
@Path("/notifications")
public class SuppressionResource {

    private final SuppressionStore store;
    private final CurrentPrincipal principal;

    @Inject
    public SuppressionResource(SuppressionStore store, CurrentPrincipal principal) {
        this.store = store;
        this.principal = principal;
    }

    // ===== Mute endpoints =====

    /**
     * Add a mute rule.
     *
     * <p>userId and tenancyId are overridden from CurrentPrincipal — never from request body.
     *
     * @param input mute rule input
     * @return created mute rule (201)
     */
    @POST
    @Path("/mute")
    public Response addMute(MuteRuleInput input) {
        // Override userId/tenancyId from principal
        var sanitizedInput = new MuteRuleInput(
            principal.actorId(),
            principal.tenancyId(),
            input.scope(),
            input.scopeId(),
            input.entityType(),
            input.expiresAt()
        );

        MuteRule rule = store.addMute(sanitizedInput);
        return Response.status(201).entity(rule).build();
    }

    /**
     * Get all active mute rules for the current user.
     *
     * @return list of active mute rules (may be empty)
     */
    @GET
    @Path("/mute")
    public List<MuteRule> listMutes() {
        return store.activeMutes(principal.actorId(), principal.tenancyId());
    }

    /**
     * Remove a mute rule.
     *
     * @param id mute rule id
     * @return 204 if removed, 404 if not found or not owned by user
     */
    @DELETE
    @Path("/mute/{id}")
    public Response removeMute(@PathParam("id") String id) {
        boolean removed = store.removeMute(id, principal.actorId(), principal.tenancyId());
        if (removed) {
            return Response.noContent().build();
        } else {
            return Response.status(404).build();
        }
    }

    // ===== Snooze endpoints =====

    /**
     * Activate snooze. Replaces any existing snooze for the user.
     *
     * <p>userId and tenancyId are overridden from CurrentPrincipal — never from request body.
     *
     * @param input snooze input
     * @return created/updated snooze record (201)
     */
    @POST
    @Path("/snooze")
    public Response activateSnooze(SnoozeInput input) {
        // Override userId/tenancyId from principal
        var sanitizedInput = new SnoozeInput(
            principal.actorId(),
            principal.tenancyId(),
            input.until()
        );

        Snooze snooze = store.activateSnooze(sanitizedInput);
        return Response.status(201).entity(snooze).build();
    }

    /**
     * Get active snooze for the current user.
     *
     * @return active snooze (200), or 404 if no active snooze
     */
    @GET
    @Path("/snooze")
    public Response getSnooze() {
        return store.activeSnooze(principal.actorId(), principal.tenancyId())
            .map(snooze -> Response.ok(snooze).build())
            .orElse(Response.status(404).build());
    }

    /**
     * Cancel snooze.
     *
     * @return 204 if cancelled, 404 if no active snooze
     */
    @DELETE
    @Path("/snooze")
    public Response cancelSnooze() {
        boolean cancelled = store.cancelSnooze(principal.actorId(), principal.tenancyId());
        if (cancelled) {
            return Response.noContent().build();
        } else {
            return Response.status(404).build();
        }
    }
}
