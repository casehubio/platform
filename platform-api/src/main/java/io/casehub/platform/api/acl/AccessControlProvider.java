package io.casehub.platform.api.acl;

import java.time.Instant;
import java.util.List;

public interface AccessControlProvider {

    default boolean canAccess(String actorId, ResourceId resourceId, AclAction action) {
        return true;
    }

    default void grant(String actorId, ResourceId resourceId, AclAction action, Instant expires) {
    }

    default void grantBatch(java.util.Collection<AclEntryRequest> requests) {
        requests.forEach(r -> grant(r.actorId(), r.resourceId(), r.action(), r.expiresAt()));
    }

    default void revoke(String actorId, ResourceId resourceId, AclAction action) {
    }

    default void revokeBatch(java.util.Collection<AclEntryRequest> requests) {
        requests.forEach(r -> revoke(r.actorId(), r.resourceId(), r.action()));
    }

    default void deny(String actorId, ResourceId resourceId, AclAction action, Instant expires) {
    }

    default void removeDeny(String actorId, ResourceId resourceId, AclAction action) {
    }

    default void denyBatch(java.util.Collection<AclEntryRequest> requests) {
        requests.forEach(r -> deny(r.actorId(), r.resourceId(), r.action(), r.expiresAt()));
    }

    default void removeDenyBatch(java.util.Collection<AclEntryRequest> requests) {
        requests.forEach(r -> removeDeny(r.actorId(), r.resourceId(), r.action()));
    }

    default void revokeAll(String actorId, ResourceId resourceId) {
    }

    default void registerParent(ResourceId childResourceId, ResourceId parentResourceId) {
    }

    default List<ResourceId> accessibleResources(String actorId, String resourceType, AclAction action) {
        return List.of();
    }

    default AclPage accessibleResources(AclQuery query) {
        java.util.List<ResourceId> all = accessibleResources(query.actorId(), query.resourceType(), query.action());
        java.util.List<ResourceId> sorted = all.stream()
                                               .sorted(java.util.Comparator.comparing(ResourceId::toString))
                                               .toList();
        java.util.List<ResourceId> filtered = query.cursor() == null
                                              ? sorted
                                              : sorted.stream().filter(r -> r.toString().compareTo(query.cursor()) > 0).toList();
        int limit = query.limit();
        if (filtered.size() <= limit) {
            return new AclPage(filtered, null);
        }
        java.util.List<ResourceId> page = filtered.subList(0, limit);
        return new AclPage(page, page.getLast().toString());
    }

    default java.util.List<ResourceId> accessibleResourcesIncludingInherited(String actorId, String resourceType, AclAction action) {
        return accessibleResources(actorId, resourceType, action);
    }

}
