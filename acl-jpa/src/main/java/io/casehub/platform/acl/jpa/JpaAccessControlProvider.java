package io.casehub.platform.acl.jpa;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
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

    @Override
    public boolean canAccess(String actorId, String resourceId, AclAction action) {
        Set<String> candidates = buildCandidateSet(actorId);
        return canAccessWithCandidates(candidates, resourceId, action, 0);
    }

    @Override
    @Transactional
    public void grant(String actorId, String resourceId, AclAction action, Instant expires) {
        Instant now       = Instant.now();
        String  tenancyId = principal.tenancyId();

        List<AclEntryEntity> existing = AclEntryEntity.list(
                "actorId = ?1 and resourceId = ?2 and action = ?3 and tenancyId = ?4",
                actorId, resourceId, action.name(), tenancyId);
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
            entry.grantedAt  = now;
            entry.expiresAt  = expires;
            entry.tenancyId  = tenancyId;
            entry.persist();
        }

        AclAuditLogEntity log = new AclAuditLogEntity();
        log.actorId     = actorId;
        log.resourceId  = resourceId;
        log.action      = action.name();
        log.operation   = "GRANT";
        log.performedBy = principal.actorId();
        log.performedAt = now;
        log.expiresAt   = expires;
        log.tenancyId   = tenancyId;
        log.persist();
    }

    @Override
    @Transactional
    public void grantBatch(java.util.Collection<io.casehub.platform.api.acl.GrantRequest> requests) {
        requests.forEach(r -> grant(r.actorId(), r.resourceId(), r.action(), r.expiresAt()));
    }


    @Override
    @Transactional
    public void revoke(String actorId, String resourceId, AclAction action) {
        String tenancyId = principal.tenancyId();
        long count = AclEntryEntity.delete(
                "actorId = ?1 and resourceId = ?2 and action = ?3 and tenancyId = ?4",
                actorId, resourceId, action.name(), tenancyId);
        if (count > 0) {
            AclAuditLogEntity log = new AclAuditLogEntity();
            log.actorId     = actorId;
            log.resourceId  = resourceId;
            log.action      = action.name();
            log.operation   = "REVOKE";
            log.performedBy = principal.actorId();
            log.performedAt = Instant.now();
            log.tenancyId   = tenancyId;
            log.persist();
        }
    }

    @Override
    @Transactional
    public void revokeBatch(java.util.Collection<io.casehub.platform.api.acl.GrantRequest> requests) {
        requests.forEach(r -> revoke(r.actorId(), r.resourceId(), r.action()));
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
            log.operation   = "REVOKE";
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
        if (shouldFilterByTenant()) {
            return AclEntryEntity.find(
                                         "select distinct e.resourceId from AclEntryEntity e " +
                                         "where e.action in ?1 " +
                                         "and (e.expiresAt is null or e.expiresAt > ?2) " +
                                         "and e.actorId in ?3 " +
                                         "and e.resourceId like ?4 escape '\\' " +
                                         "and e.tenancyId = ?5",
                                         satisfyingActions, Instant.now(), candidates, prefix, principal.tenancyId())
                                 .project(String.class)
                                 .list();
        }
        return AclEntryEntity.find(
                                     "select distinct e.resourceId from AclEntryEntity e " +
                                     "where e.action in ?1 " +
                                     "and (e.expiresAt is null or e.expiresAt > ?2) " +
                                     "and e.actorId in ?3 " +
                                     "and e.resourceId like ?4 escape '\\'",
                                     satisfyingActions, Instant.now(), candidates, prefix)
                             .project(String.class)
                             .list();}

    @Override
    public AclPage accessibleResources(AclQuery query) {
        Set<String>  candidates        = buildCandidateSet(query.actorId());
        String       escaped           = query.resourceType().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String       prefix            = escaped + ":%";
        List<String> satisfyingActions = query.action().satisfiedBy().stream().map(Enum::name).toList();
        int          fetchLimit        = query.limit() + 1;

        List<String> results;
        if (query.cursor() != null) {
            if (shouldFilterByTenant()) {
                results = AclEntryEntity.find(
                                                "select distinct e.resourceId from AclEntryEntity e " +
                                                "where e.action in ?1 " +
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
                                                "where e.action in ?1 " +
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
                                                "where e.action in ?1 " +
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
                                                "where e.action in ?1 " +
                                                "and (e.expiresAt is null or e.expiresAt > ?2) " +
                                                "and e.actorId in ?3 " +
                                                "and e.resourceId like ?4 escape '\\' " +
                                                "order by e.resourceId",
                                                satisfyingActions, Instant.now(), candidates, prefix)
                                        .page(0, fetchLimit)
                                        .project(String.class).list();
            }
        }

        if (results.size() > query.limit()) {
            List<String> page = results.subList(0, query.limit());
            return new AclPage(page, page.getLast());
        }
        return new AclPage(results, null);
    }


    private Set<String> buildCandidateSet(String actorId) {
        Set<String> candidates = new HashSet<>();
        candidates.add(actorId);
        for (String group : groupMembership.groupsOf(actorId, principal.tenancyId())) {
            candidates.add("group:" + group);
        }
        return candidates;
    }

    private boolean canAccessWithCandidates(Set<String> candidates, String resourceId,
                                            AclAction action, int depth) {
        if (depth > 20) {return false;}

        List<String> satisfyingActions = action.satisfiedBy().stream().map(Enum::name).toList();

        if (shouldFilterByTenant()) {
            long count = AclEntryEntity.count(
                    "actorId in ?1 and resourceId = ?2 and action in ?3 " +
                    "and (expiresAt is null or expiresAt > ?4) and tenancyId = ?5",
                    candidates, resourceId, satisfyingActions, Instant.now(), principal.tenancyId());
            if (count > 0) {return true;}
        } else {
            long count = AclEntryEntity.count(
                    "actorId in ?1 and resourceId = ?2 and action in ?3 " +
                    "and (expiresAt is null or expiresAt > ?4)",
                    candidates, resourceId, satisfyingActions, Instant.now());
            if (count > 0) {return true;}
        }

        ResourceParentEntity parent = ResourceParentEntity.findById(
                new ResourceParentKey(resourceId, principal.tenancyId()));
        if (parent != null) {
            return canAccessWithCandidates(candidates, parent.parentResourceId, action, depth + 1);
        }
        return false;}

    private boolean shouldFilterByTenant() {
        return !principal.isCrossTenantAdmin();
    }
}
