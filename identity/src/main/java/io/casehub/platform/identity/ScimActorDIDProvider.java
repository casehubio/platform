package io.casehub.platform.identity;

import io.casehub.platform.api.identity.ActorDIDProvider;
import io.casehub.platform.api.identity.ActorDIDSource;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

/**
 * Resolves actorId → DID URI by querying a SCIM2 Agent endpoint.
 *
 * <p>Config prefix: {@code casehub.identity.scim.*}
 *
 * <p>Delegates to {@link ScimAgentLookup} for HTTP client, parsing, and caching.
 *
 * <p>To invalidate on key rotation, call {@link #invalidate(String)}
 * from the application layer (e.g. a ledger key-rotation observer).
 */
@ApplicationScoped
@ActorDIDSource
@Priority(200)
public class ScimActorDIDProvider implements ActorDIDProvider {

    private final ScimAgentLookup lookup;

    @Inject
    public ScimActorDIDProvider(final ScimAgentLookup lookup) {
        this.lookup = lookup;
    }

    /** Required by CDI for proxy generation. Must not be called directly. */
    protected ScimActorDIDProvider() {
        this.lookup = null;
    }

    @Override
    public Optional<String> didFor(final String actorId) {
        if (lookup == null || !lookup.isConfigured()) return Optional.empty();
        return lookup.get(actorId).map(ScimAgentResource::did);
    }

    @Override
    public void invalidate(final String actorId) {
        if (lookup != null) {
            lookup.invalidate(actorId);
        }
    }
}
