package io.casehub.platform.acl.admin;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.AclEntryRequest;
import io.casehub.platform.api.acl.AclPage;
import io.casehub.platform.api.acl.AclQuery;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@Path("/acl")
@RunOnVirtualThread
public class AclResource {

    private final AccessControlProvider acl;
    private final CurrentPrincipal      principal;

    @Inject
    public AclResource(AccessControlProvider acl, CurrentPrincipal principal) {
        this.acl       = acl;
        this.principal = principal;
    }

    // --- Grants ---

    @POST
    @Path("/grants")
    @RolesAllowed("admin")
    public Response grant(AclEntryInput input) {
        acl.grant(input.actorId(), input.resourceId(), input.action(), input.expiresAt());
        return Response.noContent().build();
    }

    @POST
    @Path("/grants/batch")
    @RolesAllowed("admin")
    public Response grantBatch(List<AclEntryInput> inputs) {
        acl.grantBatch(inputs.stream().map(AclResource::toRequest).toList());
        return Response.noContent().build();
    }

    @DELETE
    @Path("/grants")
    @RolesAllowed("admin")
    public Response revoke(@QueryParam("actorId") String actorId,
                           @QueryParam("resourceId") String resourceId,
                           @QueryParam("action") AclAction action) {
        if (actorId == null || resourceId == null || action == null) {
            return Response.status(400).entity(Map.of("error", "actorId, resourceId, and action are required")).build();
        }
        acl.revoke(actorId, resourceId, action);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/grants/batch")
    @RolesAllowed("admin")
    public Response revokeBatch(List<AclEntryInput> inputs) {
        acl.revokeBatch(inputs.stream().map(AclResource::toRequest).toList());
        return Response.noContent().build();
    }

    @DELETE
    @Path("/grants/all")
    @RolesAllowed("admin")
    public Response revokeAll(@QueryParam("actorId") String actorId,
                              @QueryParam("resourceId") String resourceId) {
        if (actorId == null || resourceId == null) {
            return Response.status(400).entity(Map.of("error", "actorId and resourceId are required")).build();
        }
        acl.revokeAll(actorId, resourceId);
        return Response.noContent().build();
    }

    // --- Denies ---

    @POST
    @Path("/denies")
    @RolesAllowed("admin")
    public Response deny(AclEntryInput input) {
        acl.deny(input.actorId(), input.resourceId(), input.action(), input.expiresAt());
        return Response.noContent().build();
    }

    @POST
    @Path("/denies/batch")
    @RolesAllowed("admin")
    public Response denyBatch(List<AclEntryInput> inputs) {
        acl.denyBatch(inputs.stream().map(AclResource::toRequest).toList());
        return Response.noContent().build();
    }

    @DELETE
    @Path("/denies")
    @RolesAllowed("admin")
    public Response removeDeny(@QueryParam("actorId") String actorId,
                               @QueryParam("resourceId") String resourceId,
                               @QueryParam("action") AclAction action) {
        if (actorId == null || resourceId == null || action == null) {
            return Response.status(400).entity(Map.of("error", "actorId, resourceId, and action are required")).build();
        }
        acl.removeDeny(actorId, resourceId, action);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/denies/batch")
    @RolesAllowed("admin")
    public Response removeDenyBatch(List<AclEntryInput> inputs) {
        acl.removeDenyBatch(inputs.stream().map(AclResource::toRequest).toList());
        return Response.noContent().build();
    }

    // --- Parents ---

    @POST
    @Path("/parents")
    @RolesAllowed("admin")
    public Response registerParent(ParentInput input) {
        acl.registerParent(input.childResourceId(), input.parentResourceId());
        return Response.noContent().build();
    }

    // --- Queries ---

    @GET
    @Path("/check")
    public Response check(@QueryParam("actorId") String actorId,
                          @QueryParam("resourceId") String resourceId,
                          @QueryParam("action") AclAction action) {
        if (actorId == null || resourceId == null || action == null) {
            return Response.status(400).entity(Map.of("error", "actorId, resourceId, and action are required")).build();
        }
        if (!isAdminOrSelf(actorId)) {
            return Response.status(403).entity(Map.of("error", "Access denied")).build();
        }
        boolean allowed = acl.canAccess(actorId, resourceId, action);
        return Response.ok(new AccessCheckResponse(allowed)).build();
    }

    @GET
    @Path("/accessible")
    public Response accessible(@QueryParam("actorId") String actorId,
                               @QueryParam("resourceType") String resourceType,
                               @QueryParam("action") AclAction action,
                               @QueryParam("cursor") String cursor,
                               @QueryParam("limit") Integer limit) {
        if (actorId == null || resourceType == null || action == null) {
            return Response.status(400).entity(Map.of("error", "actorId, resourceType, and action are required")).build();
        }
        if (!isAdminOrSelf(actorId)) {
            return Response.status(403).entity(Map.of("error", "Access denied")).build();
        }
        AclPage page = acl.accessibleResources(new AclQuery(actorId, resourceType, action, cursor, limit != null ? limit : 100));
        return Response.ok(page).build();
    }

    private boolean isAdminOrSelf(String actorId) {
        return principal.groups().contains("admin") || principal.actorId().equals(actorId);
    }

    private static AclEntryRequest toRequest(AclEntryInput input) {
        return new AclEntryRequest(input.actorId(), input.resourceId(), input.action(), input.expiresAt());
    }
}
