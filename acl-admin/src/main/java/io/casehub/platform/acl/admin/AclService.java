package io.casehub.platform.acl.admin;

import io.casehub.platform.api.acl.AccessCheckResponse;
import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.AclApi;
import io.casehub.platform.api.acl.AclEntryRequest;
import io.casehub.platform.api.acl.AclPage;
import io.casehub.platform.api.acl.AclQuery;
import io.casehub.platform.api.acl.ResourceId;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.PlatformRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;

import java.util.List;

@ApplicationScoped
public class AclService implements AclApi {

    private final AccessControlProvider acl;
    private final CurrentPrincipal principal;

    @Inject
    public AclService(AccessControlProvider acl, CurrentPrincipal principal) {
        this.acl = acl;
        this.principal = principal;
    }

    @Override
    @RolesAllowed(PlatformRoles.ADMIN)
    public void grant(AclEntryRequest input) {
        acl.grant(input.actorId(), input.resourceId(), input.action(), input.expiresAt());
    }

    @Override
    @RolesAllowed(PlatformRoles.ADMIN)
    public void grantBatch(List<AclEntryRequest> inputs) {
        acl.grantBatch(inputs);
    }

    @Override
    @RolesAllowed(PlatformRoles.ADMIN)
    public void revoke(String actorId, ResourceId resourceId, AclAction action) {
        requireNonNull(actorId, resourceId, action);
        acl.revoke(actorId, resourceId, action);
    }

    @Override
    @RolesAllowed(PlatformRoles.ADMIN)
    public void revokeBatch(List<AclEntryRequest> inputs) {
        acl.revokeBatch(inputs);
    }

    @Override
    @RolesAllowed(PlatformRoles.ADMIN)
    public void revokeAll(String actorId, ResourceId resourceId) {
        requireNonNull(actorId, resourceId);
        acl.revokeAll(actorId, resourceId);
    }

    @Override
    @RolesAllowed(PlatformRoles.ADMIN)
    public void deny(AclEntryRequest input) {
        acl.deny(input.actorId(), input.resourceId(), input.action(), input.expiresAt());
    }

    @Override
    @RolesAllowed(PlatformRoles.ADMIN)
    public void denyBatch(List<AclEntryRequest> inputs) {
        acl.denyBatch(inputs);
    }

    @Override
    @RolesAllowed(PlatformRoles.ADMIN)
    public void removeDeny(String actorId, ResourceId resourceId, AclAction action) {
        requireNonNull(actorId, resourceId, action);
        acl.removeDeny(actorId, resourceId, action);
    }

    @Override
    @RolesAllowed(PlatformRoles.ADMIN)
    public void removeDenyBatch(List<AclEntryRequest> inputs) {
        acl.removeDenyBatch(inputs);
    }

    @Override
    @RolesAllowed(PlatformRoles.ADMIN)
    public void registerParent(String childResourceId, String parentResourceId) {
        acl.registerParent(ResourceId.parse(childResourceId), ResourceId.parse(parentResourceId));
    }

    @Override
    public AccessCheckResponse check(String actorId, ResourceId resourceId, AclAction action) {
        requireNonNull(actorId, resourceId, action);
        requireAdminOrSelf(actorId);
        return new AccessCheckResponse(acl.canAccess(actorId, resourceId, action));
    }

    @Override
    public AclPage accessible(String actorId, String resourceType, AclAction action, String cursor, Integer limit) {
        requireNonNull(actorId, resourceType, action);
        requireAdminOrSelf(actorId);
        return acl.accessibleResources(new AclQuery(actorId, resourceType, action, cursor, limit != null ? limit : 100));
    }

    private void requireAdminOrSelf(String actorId) {
        if (!principal.groups().contains(PlatformRoles.ADMIN) && !principal.actorId().equals(actorId)) {
            throw new ForbiddenException("Access denied");
        }
    }

    private static void requireNonNull(Object... args) {
        for (Object arg : args) {
            if (arg == null) throw new BadRequestException("Required parameter is null");
        }
    }
}
