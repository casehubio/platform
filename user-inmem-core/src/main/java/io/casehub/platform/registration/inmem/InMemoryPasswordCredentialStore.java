package io.casehub.platform.registration.inmem;

import io.casehub.platform.registration.PasswordCredential;
import io.casehub.platform.registration.PasswordCredentialStore;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPasswordCredentialStore implements PasswordCredentialStore {

    private final ConcurrentHashMap<String, PasswordCredential> store = new ConcurrentHashMap<>();

    private static String key(String actorId, String tenancyId) {
        return actorId + "|" + tenancyId;
    }

    @Override
    public void store(PasswordCredential credential) {
        store.put(key(credential.actorId(), credential.tenancyId()), credential);
    }

    @Override
    public Optional<PasswordCredential> findByActorId(String actorId, String tenancyId) {
        return Optional.ofNullable(store.get(key(actorId, tenancyId)));
    }

    @Override
    public void updatePassword(String actorId, String tenancyId, String hashedPassword) {
        var existing = store.get(key(actorId, tenancyId));
        if (existing != null) {
            store.put(key(actorId, tenancyId), new PasswordCredential(
                    actorId, tenancyId, hashedPassword, existing.createdAt(), existing.lastUsedAt()));
        }
    }

    @Override
    public void updateLastUsed(String actorId, String tenancyId) {
        var existing = store.get(key(actorId, tenancyId));
        if (existing != null) {
            store.put(key(actorId, tenancyId), new PasswordCredential(
                    actorId, tenancyId, existing.hashedPassword(), existing.createdAt(), Instant.now()));
        }
    }

    @Override
    public void delete(String actorId, String tenancyId) {
        store.remove(key(actorId, tenancyId));
    }
}
