package io.casehub.platform.notification.rest;

import io.casehub.platform.api.delivery.DigestBuffer;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST endpoints for digest buffer status.
 *
 * <p>All endpoints use {@link CurrentPrincipal} for identity — userId and tenancyId
 * are never passed as request parameters. Tenant isolation is enforced by the principal.
 */
@ApplicationScoped
@Path("/notifications/digest")
public class DigestStatusResource {

    private final DigestBuffer digestBuffer;
    private final CurrentPrincipal principal;

    @Inject
    public DigestStatusResource(DigestBuffer digestBuffer, CurrentPrincipal principal) {
        this.digestBuffer = digestBuffer;
        this.principal = principal;
    }

    /**
     * Get digest status for the current user.
     *
     * <p>Returns a map of channelId to pending notification count.
     *
     * @return map of channelId to count
     */
    @GET
    @Path("/status")
    public Map<String, Integer> status() {
        String userId = principal.actorId();
        String tenancyId = principal.tenancyId();

        Map<String, Integer> result = new LinkedHashMap<>();
        for (var key : digestBuffer.pendingKeysForUser(userId, tenancyId)) {
            int count = digestBuffer.pendingCount(key);
            if (count > 0) {
                result.put(key.channelId(), count);
            }
        }
        return result;
    }
}
