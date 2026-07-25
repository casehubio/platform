package io.casehub.platform.acl.jpa;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
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
        Instant now = Instant.now();

        List<AclEntryEntity> existing = AclEntryEntity.list(
                "actorId = ?1 and resourceId = ?2 and action = ?3",
                actorId, resourceId, action.name());
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
            entry.tenancyId  = principal.tenancyId();
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
        log.tenancyId   = principal.tenancyId();
        log.persist();
    }

    @Override
    @Transactional
    public void revoke(String actorId, String resourceId, AclAction action) {
        long count = AclEntryEntity.delete(
                "actorId = ?1 and resourceId = ?2 and action = ?3",
                actorId, resourceId, action.name());
        if (count > 0) {
            AclAuditLogEntity log = new AclAuditLogEntity();
            log.actorId     = actorId;
            log.resourceId  = resourceId;
            log.action      = action.name();
            log.operation   = "REVOKE";
            log.performedBy = principal.actorId();
            log.performedAt = Instant.now();
            log.tenancyId   = principal.tenancyId();
            log.persist();
        }
    }

    @Override
    @Transactional
    public void revokeAll(String actorId, String resourceId) {
        Instant now = Instant.now();
        List<AclEntryEntity> entries = AclEntryEntity.list(
                "actorId = ?1 and resourceId = ?2", actorId, resourceId);
        for (AclEntryEntity entry : entries) {
            AclAuditLogEntity log = new AclAuditLogEntity();
            log.actorId     = actorId;
            log.resourceId  = resourceId;
            log.action      = entry.action;
            log.operation   = "REVOKE";
            log.performedBy = principal.actorId();
            log.performedAt = now;
            log.tenancyId   = principal.tenancyId();
            log.persist();
        }
        AclEntryEntity.delete("actorId = ?1 and resourceId = ?2", actorId, resourceId);
    }

    @Override
    @Transactional
    public void registerParent(String childResourceId, String parentResourceId) {
        ResourceParentEntity existing = ResourceParentEntity.findById(childResourceId);
        if (existing == null) {
            ResourceParentEntity rp = new ResourceParentEntity();
            rp.childResourceId  = childResourceId;
            rp.parentResourceId = parentResourceId;
            rp.tenancyId        = principal.tenancyId();
            rp.persist();
        } else {
            existing.parentResourceId = parentResourceId;
            existing.persist();
        }
    }

    @Override
    public List<String> accessibleResources(String actorId, String resourceType, AclAction action) {
        Set<String> candidates = buildCandidateSet(actorId);
        String      escaped    = resourceType.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String      prefix     = escaped + ":%";
        return AclEntryEntity.find(
                                     "select distinct e.resourceId from AclEntryEntity e " +
                                     "where e.action = ?1 " +
                                     "and (e.expiresAt is null or e.expiresAt > ?2) " +
                                     "and e.actorId in ?3 " +
                                     "and e.resourceId like ?4 escape '\\'",
                                     action.name(), Instant.now(), candidates, prefix)
                             .project(String.class)
                             .list();
    }

    private Set<String> buildCandidateSet(String actorId) {
        Set<String> candidates = new HashSet<>();
        candidates.add(actorId);
        for (String group : groupMembership.groupsOf(actorId)) {
            candidates.add("group:" + group);
        }
        return candidates;
    }

    private boolean canAccessWithCandidates(Set<String> candidates, String resourceId,
                                            AclAction action, int depth) {
        if (depth > 20) {return false;}

        long count = AclEntryEntity.count(
                "actorId in ?1 and resourceId = ?2 and action = ?3 " +
                "and (expiresAt is null or expiresAt > ?4)",
                candidates, resourceId, action.name(), Instant.now());
        if (count > 0) {return true;}

        ResourceParentEntity parent = ResourceParentEntity.findById(resourceId);
        if (parent != null) {
            return canAccessWithCandidates(candidates, parent.parentResourceId, action, depth + 1);
        }
        return false;
    }
}
