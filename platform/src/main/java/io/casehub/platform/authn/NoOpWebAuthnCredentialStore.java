package io.casehub.platform.authn;

import io.casehub.platform.api.authn.WebAuthnCredential;
import io.casehub.platform.api.authn.WebAuthnCredentialStore;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class NoOpWebAuthnCredentialStore implements WebAuthnCredentialStore {

    @Override
    public void store(WebAuthnCredential credential) {}

    @Override
    public Optional<WebAuthnCredential> findByCredentialId(String credentialId) {
        return Optional.empty();
    }

    @Override
    public List<WebAuthnCredential> findByActorId(String actorId, String tenancyId) {
        return List.of();
    }

    @Override
    public void updateAfterAuthentication(String credentialId, long newSignCount, Instant lastUsedAt) {}

    @Override
    public void delete(String credentialId) {}
}
