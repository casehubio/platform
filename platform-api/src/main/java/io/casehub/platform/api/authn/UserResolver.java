package io.casehub.platform.api.authn;

import io.casehub.platform.api.identity.PrincipalId;

import java.util.Optional;

public interface UserResolver {
    Optional<PrincipalId> resolveByEmail(String email, String tenancyId);
}
