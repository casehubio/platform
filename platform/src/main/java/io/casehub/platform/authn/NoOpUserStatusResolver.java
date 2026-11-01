package io.casehub.platform.authn;

import io.casehub.platform.api.authn.UserStatusResolver;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class NoOpUserStatusResolver implements UserStatusResolver {

    @Override
    public Optional<String> resolveStatus(String actorId, String tenancyId) {
        return Optional.empty();
    }
}
