package io.casehub.platform.identity;

import io.casehub.platform.api.identity.ActorDIDProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/** Default no-op. Zero behavior change for consumers that don't configure DIDs. */
@ApplicationScoped
@DefaultBean
public class NoOpActorDIDProvider implements ActorDIDProvider {
    @Override
    public Optional<String> didFor(final String actorId) {
        return Optional.empty();
    }
}
