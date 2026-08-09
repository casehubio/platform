package io.casehub.platform.acl.inmem;

import io.casehub.platform.api.acl.WorkerCredential;
import io.casehub.platform.api.acl.WorkerCredentialStore;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Alternative
@Priority(10)
@ApplicationScoped
public class InMemoryWorkerCredentialStore implements WorkerCredentialStore {

    private final ConcurrentHashMap<String, WorkerCredential> store = new ConcurrentHashMap<>();

    @Override
    public void store(WorkerCredential credential) {
        evictExpired();
        store.put(credential.token(), credential);
    }

    @Override
    public Optional<WorkerCredential> lookup(String token) {
        evictExpired();
        return Optional.ofNullable(store.get(token));
    }

    @Override
    public void revoke(String token) {
        store.remove(token);
    }

    @Override
    public List<WorkerCredential> revokeByCase(UUID caseId) {
        var revoked = store.values().stream().filter(c -> c.caseId().equals(caseId)).toList();
        revoked.forEach(c -> store.remove(c.token()));
        return revoked;
    }

    @Override
    public List<WorkerCredential> revokeByActor(String actorId) {
        var revoked = store.values().stream().filter(c -> c.actorId().equals(actorId)).toList();
        revoked.forEach(c -> store.remove(c.token()));
        return revoked;
    }

    @Override
    public List<WorkerCredential> findActiveByActorAndCase(String actorId, UUID caseId) {
        return store.values().stream()
            .filter(c -> c.actorId().equals(actorId) && c.caseId().equals(caseId) && !c.isExpired())
            .toList();
    }

    private void evictExpired() {
        store.values().removeIf(WorkerCredential::isExpired);
    }
}
