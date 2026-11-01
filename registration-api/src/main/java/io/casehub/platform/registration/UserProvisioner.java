package io.casehub.platform.registration;

import io.casehub.platform.api.identity.PrincipalId;

import java.util.Optional;

public interface UserProvisioner {
    Optional<PrincipalId> provisionFromSocialLogin(
        String email, String tenancyId, String provider, boolean emailVerified);
}
