package io.casehub.platform.registration.quarkus;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.PlatformRoles;
import io.casehub.platform.api.mcp.HttpMethod;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestMethod;
import io.casehub.platform.registration.InvitationCreationResult;
import io.casehub.platform.registration.InvitationRecord;
import io.casehub.platform.registration.InvitationRequest;
import io.casehub.platform.registration.InvitationStore;
import io.casehub.platform.registration.RegistrationService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
@McpDomain("invitations")
@RolesAllowed(PlatformRoles.ADMIN)
public class InvitationEndpoint {

    @Inject
    RegistrationService registrationService;

    @Inject
    InvitationStore invitationStore;

    @Inject
    CurrentPrincipal principal;

    @PlatformMutation("Create an invitation")
    public InvitationCreationResult createInvitation(InvitationRequest request) {
        return registrationService.createInvitation(request);
    }

    @PlatformMutation("Revoke an invitation")
    @RestMethod(HttpMethod.DELETE)
    public void revokeInvitation(String id) {
        registrationService.revokeInvitation(id);
    }

    @PlatformQuery("List invitations for the current tenant")
    public List<InvitationRecord> listInvitations() {
        return invitationStore.findByTenancy(principal.tenancyId());
    }
}
