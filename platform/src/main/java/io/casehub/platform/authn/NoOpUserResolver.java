package io.casehub.platform.authn;

import io.casehub.platform.api.authn.UserResolver;
import io.casehub.platform.api.identity.PrincipalId;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class NoOpUserResolver implements UserResolver {

    @Override
    public Optional<PrincipalId> resolveByEmail(String email, String tenancyId) {
        return Optional.empty();
    }
}
