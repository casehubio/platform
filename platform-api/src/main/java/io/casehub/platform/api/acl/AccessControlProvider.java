package io.casehub.platform.api.acl;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface AccessControlProvider {

    default CompletionStage<Boolean> canAccess(String actorId, String resourceId, AclAction action) {
        return CompletableFuture.completedFuture(true);
    }

    default CompletionStage<Void> grant(String actorId, String resourceId, AclAction action, Instant expires) {
        return CompletableFuture.completedFuture(null);
    }

    default CompletionStage<Void> revoke(String actorId, String resourceId, AclAction action) {
        return CompletableFuture.completedFuture(null);
    }

    default CompletionStage<Void> revokeAll(String actorId, String resourceId) {
        return CompletableFuture.completedFuture(null);
    }

    default CompletionStage<Void> registerParent(String childResourceId, String parentResourceId) {
        return CompletableFuture.completedFuture(null);
    }

    default CompletionStage<List<String>> accessibleResources(String actorId, String resourceType, AclAction action) {
        return CompletableFuture.completedFuture(List.of());
    }
}
