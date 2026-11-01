package io.casehub.platform.authn;

import io.casehub.platform.api.identity.PrincipalId;
import io.casehub.platform.registration.UserProvisioner;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class NoOpUserProvisioner implements UserProvisioner {

    @Override
    public Optional<PrincipalId> provisionFromSocialLogin(
            String email, String tenancyId, String provider, boolean emailVerified) {
        return Optional.empty();
    }
}
