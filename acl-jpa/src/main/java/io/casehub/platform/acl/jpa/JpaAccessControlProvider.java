package io.casehub.platform.acl.jpa;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.AclEntryRequest;
import io.casehub.platform.api.acl.AclPage;
import io.casehub.platform.api.acl.AclQuery;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class JpaAccessControlProvider implements AccessControlProvider {

    @Inject
    GroupMembershipProvider groupMembership;

    @Inject
    CurrentPrincipal principal;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    @Override
    public boolean canAccess(String actorId, String resourceId, AclAction action) {
        Set<String> candidates = buildCandidateSet(actorId);
        return resolveAccess(candidates, resourceId, action, 0);
    }

    @Override
    @Transactional
    public void grant(String actorId, String resourceId, AclAction action, Instant expires) {
        upsertEntry(actorId, resourceId, action, expires, "ALLOW", "GRANT");
    }

    @Override
    @Transactional
    public void deny(String actorId, String resourceId, AclAction action, Instant expires) {
        upsertEntry(actorId, resourceId, action, expires, "DENY", "DENY");
    }

    @Override
    @Transactional
    public void grantBatch(java.util.Collection<AclEntryRequest> requests) {
        requests.forEach(r -> grant(r.actorId(), r.resourceId(), r.action(), r.expiresAt()));
    }

    @Override
    @Transactional
    public void denyBatch(java.util.Collection<AclEntryRequest> requests) {
        requests.forEach(r -> deny(r.actorId(), r.resourceId(), r.action(), r.expiresAt()));
    }

    @Override
    @Transactional
    public void revoke(String actorId, String resourceId, AclAction action) {
        removeEntry(actorId, resourceId, action, "ALLOW", "REVOKE");
    }

    @Override
    @Transactional
    public void removeDeny(String actorId, String resourceId, AclAction action) {
        removeEntry(actorId, resourceId, action, "DENY", "REVOKE_DENY");
    }

    @Override
    @Transactional
    public void revokeBatch(java.util.Collection<AclEntryRequest> requests) {
        requests.forEach(r -> revoke(r.actorId(), r.resourceId(), r.action()));
    }

    @Override
    @Transactional
    public void removeDenyBatch(java.util.Collection<AclEntryRequest> requests) {
        requests.forEach(r -> removeDeny(r.actorId(), r.resourceId(), r.action()));
    }

    @Override
    @Transactional
    public void revokeAll(String actorId, String resourceId) {
        Instant now       = Instant.now();
        String  tenancyId = principal.tenancyId();
        List<AclEntryEntity> entries = AclEntryEntity.list(
                "actorId = ?1 and resourceId = ?2 and tenancyId = ?3",
                actorId, resourceId, tenancyId);
        for (AclEntryEntity entry : entries) {
            AclAuditLogEntity log = new AclAuditLogEntity();
            log.actorId     = actorId;
            log.resourceId  = resourceId;
            log.action      = entry.action;
            log.operation   = "ALLOW".equals(entry.entryType) ? "REVOKE" : "REVOKE_DENY";
            log.performedBy = principal.actorId();
            log.performedAt = now;
            log.tenancyId   = tenancyId;
            log.persist();
        }
        AclEntryEntity.delete("actorId = ?1 and resourceId = ?2 and tenancyId = ?3",
                              actorId, resourceId, tenancyId);
    }

    @Override
    @Transactional
    public void registerParent(String childResourceId, String parentResourceId) {
        String tenancyId = principal.tenancyId();
        ResourceParentEntity existing = ResourceParentEntity.findById(
                new ResourceParentKey(childResourceId, tenancyId));
        if (existing == null) {
            ResourceParentEntity rp = new ResourceParentEntity();
            rp.childResourceId  = childResourceId;
            rp.parentResourceId = parentResourceId;
            rp.tenancyId        = tenancyId;
            rp.persist();
        } else {
            existing.parentResourceId = parentResourceId;
            existing.persist();
        }
    }

    @Override
    public List<String> accessibleResources(String actorId, String resourceType, AclAction action) {
        Set<String>  candidates        = buildCandidateSet(actorId);
        String       escaped           = resourceType.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String       prefix            = escaped + ":%";
        List<String> satisfyingActions = action.satisfiedBy().stream().map(Enum::name).toList();
        List<String> deniedByActions   = action.deniedBy().stream().map(Enum::name).toList();
        String       wildcardId        = resourceType + ":*";

        List<String> granted;
        if (shouldFilterByTenant()) {
            granted = AclEntryEntity.find(
                                            "select distinct e.resourceId from AclEntryEntity e " +
                                            "where e.entryType = 'ALLOW' " +
                                            "and e.action in ?1 " +
                                            "and (e.expiresAt is null or e.expiresAt > ?2) " +
                                            "and e.actorId in ?3 " +
                                            "and e.resourceId like ?4 escape '\\' " +
                                            "and e.tenancyId = ?5",
                                            satisfyingActions, Instant.now(), candidates, prefix, principal.tenancyId())
                                    .project(String.class)
                                    .list();
        } else {
            granted = AclEntryEntity.find(
                                            "select distinct e.resourceId from AclEntryEntity e " +
                                            "where e.entryType = 'ALLOW' " +
                                            "and e.action in ?1 " +
                                            "and (e.expiresAt is null or e.expiresAt > ?2) " +
                                            "and e.actorId in ?3 " +
                                            "and e.resourceId like ?4 escape '\\'",
                                            satisfyingActions, Instant.now(), candidates, prefix)
                                    .project(String.class)
                                    .list();
        }

        Set<String> denied = fetchDeniedResources(candidates, deniedByActions, prefix);

        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>(granted);
        result.removeAll(denied);

        return new java.util.ArrayList<>(result);
    }

    @Override
    public AclPage accessibleResources(AclQuery query) {
        Set<String>  candidates        = buildCandidateSet(query.actorId());
        String       escaped           = query.resourceType().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String       prefix            = escaped + ":%";
        List<String> satisfyingActions = query.action().satisfiedBy().stream().map(Enum::name).toList();
        List<String> deniedByActions   = query.action().deniedBy().stream().map(Enum::name).toList();
        int          fetchLimit        = query.limit() + 1;

        Set<String> denied = fetchDeniedResources(candidates, deniedByActions, prefix);

        List<String> results;
        if (query.cursor() != null) {
            if (shouldFilterByTenant()) {
                results = AclEntryEntity.find(
                                                "select distinct e.resourceId from AclEntryEntity e " +
                                                "where e.entryType = 'ALLOW' " +
                                                "and e.action in ?1 " +
                                                "and (e.expiresAt is null or e.expiresAt > ?2) " +
                                                "and e.actorId in ?3 " +
                                                "and e.resourceId like ?4 escape '\\' " +
                                                "and e.tenancyId = ?5 " +
                                                "and e.resourceId > ?6 " +
                                                "order by e.resourceId",
                                                satisfyingActions, Instant.now(), candidates, prefix,
                                                principal.tenancyId(), query.cursor())
                                        .page(0, fetchLimit)
                                        .project(String.class).list();
            } else {
                results = AclEntryEntity.find(
                                                "select distinct e.resourceId from AclEntryEntity e " +
                                                "where e.entryType = 'ALLOW' " +
                                                "and e.action in ?1 " +
                                                "and (e.expiresAt is null or e.expiresAt > ?2) " +
                                                "and e.actorId in ?3 " +
                                                "and e.resourceId like ?4 escape '\\' " +
                                                "and e.resourceId > ?5 " +
                                                "order by e.resourceId",
                                                satisfyingActions, Instant.now(), candidates, prefix, query.cursor())
                                        .page(0, fetchLimit)
                                        .project(String.class).list();
            }
        } else {
            if (shouldFilterByTenant()) {
                results = AclEntryEntity.find(
                                                "select distinct e.resourceId from AclEntryEntity e " +
                                                "where e.entryType = 'ALLOW' " +
                                                "and e.action in ?1 " +
                                                "and (e.expiresAt is null or e.expiresAt > ?2) " +
                                                "and e.actorId in ?3 " +
                                                "and e.resourceId like ?4 escape '\\' " +
                                                "and e.tenancyId = ?5 " +
                                                "order by e.resourceId",
                                                satisfyingActions, Instant.now(), candidates, prefix, principal.tenancyId())
                                        .page(0, fetchLimit)
                                        .project(String.class).list();
            } else {
                results = AclEntryEntity.find(
                                                "select distinct e.resourceId from AclEntryEntity e " +
                                                "where e.entryType = 'ALLOW' " +
                                                "and e.action in ?1 " +
                                                "and (e.expiresAt is null or e.expiresAt > ?2) " +
                                                "and e.actorId in ?3 " +
                                                "and e.resourceId like ?4 escape '\\' " +
                                                "order by e.resourceId",
                                                satisfyingActions, Instant.now(), candidates, prefix)
                                        .page(0, fetchLimit)
                                        .project(String.class).list();
            }
        }

        results = new java.util.ArrayList<>(results);
        results.removeAll(denied);

        if (results.size() > query.limit()) {
            List<String> page = results.subList(0, query.limit());
            return new AclPage(page, page.getLast());
        }
        return new AclPage(results, null);
    }

    @Override
    public List<String> accessibleResourcesIncludingInherited(String actorId, String resourceType, AclAction action) {
        Set<String>  candidates        = buildCandidateSet(actorId);
        String       escaped           = resourceType.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        List<String> satisfyingActions = action.satisfiedBy().stream().map(Enum::name).toList();
        List<String> deniedByActions   = action.deniedBy().stream().map(Enum::name).toList();
        String       prefix            = escaped + ":%";

        List<String> directResources = accessibleResources(actorId, resourceType, action);

        Set<String> allGrantedResources = new java.util.LinkedHashSet<>();
        if (shouldFilterByTenant()) {
            List<String> tenantFilteredGrants = AclEntryEntity.find(
                                                                      "select distinct e.resourceId from AclEntryEntity e " +
                                                                      "where e.entryType = 'ALLOW' " +
                                                                      "and e.action in ?1 " +
                                                                      "and (e.expiresAt is null or e.expiresAt > ?2) " +
                                                                      "and e.actorId in ?3 " +
                                                                      "and e.tenancyId = ?4",
                                                                      satisfyingActions, Instant.now(), candidates, principal.tenancyId())
                                                              .project(String.class).list();
            allGrantedResources.addAll(tenantFilteredGrants);
        } else {
            List<String> allGrants = AclEntryEntity.find(
                                                           "select distinct e.resourceId from AclEntryEntity e " +
                                                           "where e.entryType = 'ALLOW' " +
                                                           "and e.action in ?1 " +
                                                           "and (e.expiresAt is null or e.expiresAt > ?2) " +
                                                           "and e.actorId in ?3",
                                                           satisfyingActions, Instant.now(), candidates)
                                                   .project(String.class).list();
            allGrantedResources.addAll(allGrants);
        }

        if (allGrantedResources.isEmpty()) {
            return directResources;
        }

        String nativeSql =
                "WITH RECURSIVE children AS (" +
                "  SELECT rp.child_resource_id FROM resource_parent rp " +
                "  WHERE rp.parent_resource_id IN (:grantedResources)" +
                (shouldFilterByTenant() ? " AND rp.tenancy_id = :tenancyId" : "") +
                "  UNION " +
                "  SELECT rp2.child_resource_id FROM resource_parent rp2 " +
                "  JOIN children c ON rp2.parent_resource_id = c.child_resource_id" +
                (shouldFilterByTenant() ? " WHERE rp2.tenancy_id = :tenancyId" : "") +
                ") " +
                "SELECT child_resource_id FROM children " +
                "WHERE child_resource_id LIKE :prefix";

        @SuppressWarnings("unchecked")
        jakarta.persistence.Query query = entityManager.createNativeQuery(nativeSql);
        query.setParameter("grantedResources", allGrantedResources);
        query.setParameter("prefix", escaped + ":%");
        if (shouldFilterByTenant()) {
            query.setParameter("tenancyId", principal.tenancyId());
        }

        List<String> inheritedChildren = query.getResultList();

        Set<String> denied = fetchDeniedResources(candidates, deniedByActions, prefix);

        Set<String> result = new java.util.LinkedHashSet<>(directResources);
        for (String child : inheritedChildren) {
            if (!denied.contains(child)) {
                result.add(child);
            }
        }
        return new java.util.ArrayList<>(result);
    }

    private void upsertEntry(String actorId, String resourceId, AclAction action, Instant expires,
                             String entryType, String auditOp) {
        Instant now       = Instant.now();
        String  tenancyId = principal.tenancyId();

        List<AclEntryEntity> existing = AclEntryEntity.list(
                "actorId = ?1 and resourceId = ?2 and action = ?3 and tenancyId = ?4 and entryType = ?5",
                actorId, resourceId, action.name(), tenancyId, entryType);
        if (!existing.isEmpty()) {
            AclEntryEntity entry = existing.getFirst();
            entry.expiresAt = expires;
            entry.grantedAt = now;
            entry.persist();
        } else {
            AclEntryEntity entry = new AclEntryEntity();
            entry.actorId    = actorId;
            entry.resourceId = resourceId;
            entry.action     = action.name();
            entry.entryType  = entryType;
            entry.grantedAt  = now;
            entry.expiresAt  = expires;
            entry.tenancyId  = tenancyId;
            entry.persist();
        }

        AclAuditLogEntity log = new AclAuditLogEntity();
        log.actorId     = actorId;
        log.resourceId  = resourceId;
        log.action      = action.name();
        log.operation   = auditOp;
        log.performedBy = principal.actorId();
        log.performedAt = now;
        log.expiresAt   = expires;
        log.tenancyId   = tenancyId;
        log.persist();
    }

    private void removeEntry(String actorId, String resourceId, AclAction action,
                             String entryType, String auditOp) {
        String tenancyId = principal.tenancyId();
        long count = AclEntryEntity.delete(
                "actorId = ?1 and resourceId = ?2 and action = ?3 and tenancyId = ?4 and entryType = ?5",
                actorId, resourceId, action.name(), tenancyId, entryType);
        if (count > 0) {
            AclAuditLogEntity log = new AclAuditLogEntity();
            log.actorId     = actorId;
            log.resourceId  = resourceId;
            log.action      = action.name();
            log.operation   = auditOp;
            log.performedBy = principal.actorId();
            log.performedAt = Instant.now();
            log.tenancyId   = tenancyId;
            log.persist();
        }
    }

    private Set<String> buildCandidateSet(String actorId) {
        Set<String> candidates = new HashSet<>();
        candidates.add(actorId);
        for (String group : groupMembership.groupsOf(actorId, principal.tenancyId())) {
            candidates.add("group:" + group);
        }
        return candidates;
    }

    private boolean resolveAccess(Set<String> candidates, String resourceId,
                                  AclAction action, int depth) {
        if (depth > 20) {return false;}

        int resolution = resolveAt(candidates, resourceId, action);
        if (resolution != 0) {return resolution > 0;}

        ResourceParentEntity parent = ResourceParentEntity.findById(
                new ResourceParentKey(resourceId, principal.tenancyId()));
        if (parent != null) {
            return resolveAccess(candidates, parent.parentResourceId, action, depth + 1);
        }
        return false;
    }

    private int resolveAt(Set<String> candidates, String resourceId, AclAction action) {
        List<String> deniedByActions   = action.deniedBy().stream().map(Enum::name).toList();
        List<String> satisfyingActions = action.satisfiedBy().stream().map(Enum::name).toList();

        // 1. Instance deny
        if (hasEntry(candidates, resourceId, deniedByActions, "DENY")) {return -1;}
        // 2. Instance grant
        if (hasEntry(candidates, resourceId, satisfyingActions, "ALLOW")) {return 1;}

        // 3-4. Wildcard
        int colonIndex = resourceId.indexOf(':');
        if (colonIndex > 0) {
            String wildcardId = resourceId.substring(0, colonIndex) + ":*";
            if (!wildcardId.equals(resourceId)) {
                // 3. Wildcard deny
                if (hasEntry(candidates, wildcardId, deniedByActions, "DENY")) {return -1;}
                // 4. Wildcard grant
                if (hasEntry(candidates, wildcardId, satisfyingActions, "ALLOW")) {return 1;}
            }
        }

        return 0;
    }

    private boolean hasEntry(Set<String> candidates, String resourceId, List<String> actions, String entryType) {
        if (shouldFilterByTenant()) {
            long count = AclEntryEntity.count(
                    "actorId in ?1 and resourceId = ?2 and action in ?3 " +
                    "and entryType = ?4 and (expiresAt is null or expiresAt > ?5) and tenancyId = ?6",
                    candidates, resourceId, actions, entryType, Instant.now(), principal.tenancyId());
            return count > 0;
        }
        long count = AclEntryEntity.count(
                "actorId in ?1 and resourceId = ?2 and action in ?3 " +
                "and entryType = ?4 and (expiresAt is null or expiresAt > ?5)",
                candidates, resourceId, actions, entryType, Instant.now());
        return count > 0;
    }

    private Set<String> fetchDeniedResources(Set<String> candidates, List<String> deniedByActions, String prefix) {
        List<String> deniedList;
        if (shouldFilterByTenant()) {
            deniedList = AclEntryEntity.find(
                                               "select distinct e.resourceId from AclEntryEntity e " +
                                               "where e.entryType = 'DENY' " +
                                               "and e.action in ?1 " +
                                               "and (e.expiresAt is null or e.expiresAt > ?2) " +
                                               "and e.actorId in ?3 " +
                                               "and e.resourceId like ?4 escape '\\' " +
                                               "and e.tenancyId = ?5",
                                               deniedByActions, Instant.now(), candidates, prefix, principal.tenancyId())
                                       .project(String.class).list();
        } else {
            deniedList = AclEntryEntity.find(
                                               "select distinct e.resourceId from AclEntryEntity e " +
                                               "where e.entryType = 'DENY' " +
                                               "and e.action in ?1 " +
                                               "and (e.expiresAt is null or e.expiresAt > ?2) " +
                                               "and e.actorId in ?3 " +
                                               "and e.resourceId like ?4 escape '\\'",
                                               deniedByActions, Instant.now(), candidates, prefix)
                                       .project(String.class).list();
        }
        return new java.util.HashSet<>(deniedList);
    }

    private boolean shouldFilterByTenant() {
        return !principal.isCrossTenantAdmin();
    }
}
