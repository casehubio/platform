package io.casehub.platform.api.acl;

import java.util.List;
import java.util.Optional;

public interface WorkerCredentialStore {

    default void store(WorkerCredential credential) {
    }

    default Optional<WorkerCredential> lookup(String token) {
        return Optional.empty();
    }

    default void revoke(String token) {
    }

    default List<WorkerCredential> revokeByResource(ResourceId resourceId) {
        return List.of();
    }

    default List<WorkerCredential> revokeByActor(String actorId) {
        return List.of();
    }

    default List<WorkerCredential> findActiveByActorAndResource(
        String actorId, ResourceId resourceId) {
        return List.of();
    }
}
