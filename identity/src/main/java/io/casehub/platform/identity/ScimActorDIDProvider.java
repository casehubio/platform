package io.casehub.platform.identity;

import io.casehub.platform.api.identity.ActorDIDProvider;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.util.Optional;

/**
 * Resolves actorId → DID URI by querying a SCIM2 Agent endpoint.
 *
 * <p>Activated via:
 * {@code quarkus.arc.selected-alternatives=io.casehub.platform.identity.ScimActorDIDProvider}
 *
 * <p>Config prefix: {@code casehub.identity.scim.*}
 *
 * <p>Delegates to {@link ScimAgentLookup} for HTTP client, parsing, and caching.
 * HTTPS is enforced via {@code @PostConstruct} — fires at first CDI instantiation,
 * not at Quarkus boot for {@code @Alternative} beans.
 *
 * <p>To invalidate on key rotation, call {@link #invalidate(String)}
 * from the application layer (e.g. a ledger key-rotation observer).
 */
@ApplicationScoped
@Alternative
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

    @PostConstruct
    public void validateEndpoint() {
        lookup.validate();
    }

    @Override
    public Optional<String> didFor(final String actorId) {
        return lookup.get(actorId).map(ScimAgentResource::did);
    }

    public void invalidate(final String actorId) {
        lookup.invalidate(actorId);
    }
}
