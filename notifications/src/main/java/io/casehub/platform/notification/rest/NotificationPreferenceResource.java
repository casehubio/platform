package io.casehub.platform.notification.rest;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.notification.settings.NotificationPreferenceStore;
import io.casehub.platform.api.notification.settings.NotificationPreferenceUpdate;
import io.casehub.platform.api.notification.settings.NotificationPreferences;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;

import java.time.Instant;
import java.util.Map;

/**
 * REST endpoints for notification user preferences.
 *
 * <p>All endpoints use {@link CurrentPrincipal} for identity — userId and tenancyId
 * are never passed as request parameters. Tenant isolation is enforced by the principal.
 */
@ApplicationScoped
@Path("/notifications/preferences")
public class NotificationPreferenceResource {

    private final NotificationPreferenceStore store;
    private final CurrentPrincipal principal;

    @Inject
    public NotificationPreferenceResource(NotificationPreferenceStore store, CurrentPrincipal principal) {
        this.store = store;
        this.principal = principal;
    }

    /**
     * Get current user's preferences.
     *
     * <p>Returns empty preferences (empty channelDefaults, no quiet hours) if none stored.
     *
     * @return notification preferences
     */
    @GET
    public NotificationPreferences get() {
        return store.get(principal.actorId(), principal.tenancyId())
            .orElseGet(() -> new NotificationPreferences(
                principal.actorId(),
                principal.tenancyId(),
                Map.of(),
                null,
                Instant.now()
            ));
    }

    /**
     * Update (upsert) user preferences.
     *
     * <p>userId and tenancyId are overridden from CurrentPrincipal — never from request body.
     *
     * @param update preference update
     * @return updated preferences
     */
    @PUT
    public NotificationPreferences update(NotificationPreferenceUpdate update) {
        return store.update(principal.actorId(), principal.tenancyId(), update);
    }
}
