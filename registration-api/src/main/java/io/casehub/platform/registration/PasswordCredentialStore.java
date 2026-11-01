package io.casehub.platform.registration;

import java.util.Optional;

public interface PasswordCredentialStore {
    void store(PasswordCredential credential);
    Optional<PasswordCredential> findByActorId(String actorId, String tenancyId);
    void updatePassword(String actorId, String tenancyId, String hashedPassword);
    void updateLastUsed(String actorId, String tenancyId);
    void delete(String actorId, String tenancyId);
}
