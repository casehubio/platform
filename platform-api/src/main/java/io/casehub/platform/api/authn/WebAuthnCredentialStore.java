package io.casehub.platform.api.authn;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WebAuthnCredentialStore {
    void store(WebAuthnCredential credential);
    Optional<WebAuthnCredential> findByCredentialId(String credentialId);
    List<WebAuthnCredential> findByActorId(String actorId, String tenancyId);
    void updateAfterAuthentication(String credentialId, long newSignCount, Instant lastUsedAt);
    void delete(String credentialId);
}
