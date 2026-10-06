package io.casehub.platform.authn.inmem;

import io.casehub.platform.api.authn.WebAuthnCredential;
import io.casehub.platform.api.authn.WebAuthnCredentialStore;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryWebAuthnCredentialStore implements WebAuthnCredentialStore {

    private final ConcurrentHashMap<String, WebAuthnCredential> credentials = new ConcurrentHashMap<>();

    @Override
    public void store(WebAuthnCredential credential) {
        credentials.put(credential.credentialId(), credential);
    }

    @Override
    public Optional<WebAuthnCredential> findByCredentialId(String credentialId) {
        return Optional.ofNullable(credentials.get(credentialId));
    }

    @Override
    public List<WebAuthnCredential> findByActorId(String actorId, String tenancyId) {
        return credentials.values().stream()
                .filter(c -> c.actorId().equals(actorId) && c.tenancyId().equals(tenancyId))
                .toList();
    }

    @Override
    public void updateAfterAuthentication(String credentialId, long newSignCount, Instant lastUsedAt) {
        credentials.computeIfPresent(credentialId, (k, c) ->
                new WebAuthnCredential(c.credentialId(), c.actorId(), c.tenancyId(),
                        c.publicKeyCose(), newSignCount, c.transports(), c.aaguid(),
                        c.displayName(), c.createdAt(), lastUsedAt, c.discoverable()));
    }

    @Override
    public void delete(String credentialId) {
        credentials.remove(credentialId);
    }

    public int size() {
        return credentials.size();
    }
}
