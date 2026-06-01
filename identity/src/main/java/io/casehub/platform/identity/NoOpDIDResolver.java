package io.casehub.platform.identity;

import io.casehub.platform.api.identity.DIDDocument;
import io.casehub.platform.api.identity.DIDResolver;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/** Default no-op. Resolves nothing — DID verification is skipped. */
@ApplicationScoped
@DefaultBean
public class NoOpDIDResolver implements DIDResolver {
    @Override
    public Optional<DIDDocument> resolve(final String did) {
        return Optional.empty();
    }
}
