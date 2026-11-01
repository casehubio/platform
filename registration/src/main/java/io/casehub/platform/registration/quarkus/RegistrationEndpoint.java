package io.casehub.platform.registration.quarkus;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.PlatformRoles;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.registration.PasswordRegistrationRequest;
import io.casehub.platform.registration.RegistrationMode;
import io.casehub.platform.registration.RegistrationPolicy;
import io.casehub.platform.registration.RegistrationResult;
import io.casehub.platform.registration.RegistrationService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@McpDomain("registration")
public class RegistrationEndpoint {

    @Inject
    RegistrationService registrationService;

    @Inject
    RegistrationPolicy registrationPolicy;

    @Inject
    CurrentPrincipal principal;

    @PlatformMutation("Register with email and password")
    public RegistrationResult registerWithPassword(PasswordRegistrationRequest request) {
        return registrationService.registerWithPassword(request);
    }

    @PlatformMutation("Verify email address")
    public RegistrationResult verifyEmail(String token) {
        return registrationService.verifyEmail(token);
    }

    @PlatformMutation("Approve a pending registration")
    @RolesAllowed(PlatformRoles.ADMIN)
    public RegistrationResult approveRegistration(String userId) {
        return registrationService.approveRegistration(userId, principal.tenancyId());
    }

    @PlatformMutation("Reject a pending registration")
    @RolesAllowed(PlatformRoles.ADMIN)
    public RegistrationResult rejectRegistration(String userId, String reason) {
        return registrationService.rejectRegistration(userId, principal.tenancyId(), reason);
    }

    @PlatformQuery("Get registration policy for a tenant")
    public RegistrationMode getPolicy(String tenancyId) {
        return registrationPolicy.getMode(tenancyId);
    }
}
