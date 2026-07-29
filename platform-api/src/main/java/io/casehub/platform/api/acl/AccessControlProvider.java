package io.casehub.platform.api.acl;

import java.time.Instant;
import java.util.List;

public interface AccessControlProvider {

    default boolean canAccess(String actorId, String resourceId, AclAction action) {
        return true;
    }

    default void grant(String actorId, String resourceId, AclAction action, Instant expires) {
    }

    default void grantBatch(java.util.Collection<GrantRequest> requests) {
        requests.forEach(r -> grant(r.actorId(), r.resourceId(), r.action(), r.expiresAt()));
    }


    default void revoke(String actorId, String resourceId, AclAction action) {
    }

    default void revokeBatch(java.util.Collection<GrantRequest> requests) {
        requests.forEach(r -> revoke(r.actorId(), r.resourceId(), r.action()));
    }


    default void revokeAll(String actorId, String resourceId) {
    }

    default void registerParent(String childResourceId, String parentResourceId) {
    }

    default List<String> accessibleResources(String actorId, String resourceType, AclAction action) {
        return List.of();
    }

    default AclPage accessibleResources(AclQuery query) {
        java.util.List<String> all    = accessibleResources(query.actorId(), query.resourceType(), query.action());
        java.util.List<String> sorted = all.stream().sorted().toList();
        java.util.List<String> filtered = query.cursor() == null
                                          ? sorted
                                          : sorted.stream().filter(id -> id.compareTo(query.cursor()) > 0).toList();
        int limit = query.limit();
        if (filtered.size() <= limit) {
            return new AclPage(filtered, null);
        }
        java.util.List<String> page = filtered.subList(0, limit);
        return new AclPage(page, page.getLast());
    }

}
