package io.casehub.platform.identity;

import io.casehub.platform.api.identity.ActorDIDProvider;
import io.casehub.platform.api.identity.ActorDIDSource;
import io.casehub.platform.identity.config.IdentityConfig;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

/**
 * Reads DID URIs from configuration.
 * Config key: {@code casehub.identity.dids."claude:reviewer@v1"=did:web:...}
 * Quote the key in application.properties to handle the colon in actorId strings.
 */
@ApplicationScoped
@ActorDIDSource
@Priority(100)
public class ConfiguredActorDIDProvider implements ActorDIDProvider {

    @Inject
    IdentityConfig config;

    @Override
    public Optional<String> didFor(final String actorId) {
        if (actorId == null) return Optional.empty();
        return Optional.ofNullable(config.dids().get(actorId));
    }
}
