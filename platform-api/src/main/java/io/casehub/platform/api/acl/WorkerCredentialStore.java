package io.casehub.platform.api.acl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerCredentialStore {

    default void store(WorkerCredential credential) {
    }

    default Optional<WorkerCredential> lookup(String token) {
        return Optional.empty();
    }

    default void revoke(String token) {
    }

    default List<WorkerCredential> revokeByCase(UUID caseId) {
        return List.of();
    }

    default List<WorkerCredential> revokeByActor(String actorId) {
        return List.of();
    }

    default List<WorkerCredential> findActiveByActorAndCase(String actorId, UUID caseId) {
        return List.of();
    }
}
