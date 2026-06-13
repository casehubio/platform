package io.casehub.platform.api.acl;

import java.time.Instant;
import java.util.List;

public interface AccessControlProvider {

    default boolean canAccess(String actorId, String resourceId, AclAction action) {
        return true;
    }

    default void grant(String actorId, String resourceId, AclAction action, Instant expires) {}

    default void revoke(String actorId, String resourceId, AclAction action) {}

    default void revokeAll(String actorId, String resourceId) {}

    default void registerParent(String childResourceId, String parentResourceId) {}

    default List<String> accessibleResources(String actorId, String resourceType, AclAction action) {
        return List.of();
    }
}
