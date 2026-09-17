package io.casehub.platform.acl.spring.jpa;

import io.casehub.platform.acl.jpa.AclAuditLogEntity;
import io.casehub.platform.acl.jpa.AclEntryEntity;
import io.casehub.platform.acl.jpa.ResourceParentEntity;
import io.casehub.platform.acl.jpa.ResourceParentKey;
import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.AclEntryRequest;
import io.casehub.platform.api.acl.AclPage;
import io.casehub.platform.api.acl.AclQuery;
import io.casehub.platform.api.acl.ResourceId;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SpringAccessControlProvider implements AccessControlProvider {

    private final AclEntryEntityRepository entryRepo;
    private final AclAuditLogEntityRepository auditRepo;
    private final ResourceParentEntityRepository parentRepo;
    private final GroupMembershipProvider groupMembership;
    private final CurrentPrincipal principal;

    public SpringAccessControlProvider(AclEntryEntityRepository entryRepo,
                                       AclAuditLogEntityRepository auditRepo,
                                       ResourceParentEntityRepository parentRepo,
                                       GroupMembershipProvider groupMembership,
                                       CurrentPrincipal principal) {
        this.entryRepo = entryRepo;
        this.auditRepo = auditRepo;
        this.parentRepo = parentRepo;
        this.groupMembership = groupMembership;
        this.principal = principal;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAccess(String actorId, ResourceId resourceId, AclAction action) {
        Set<String> candidates = buildCandidateSet(actorId);
        return resolveAccess(candidates, resourceId, action, 0);
    }

    @Override
    @Transactional
    public void grant(String actorId, ResourceId resourceId, AclAction action, Instant expires) {
        upsertEntry(actorId, resourceId, action, expires, "ALLOW", "GRANT");
    }

    @Override
    @Transactional
    public void deny(String actorId, ResourceId resourceId, AclAction action, Instant expires) {
        upsertEntry(actorId, resourceId, action, expires, "DENY", "DENY");
    }

    @Override
    @Transactional
    public void grantBatch(Collection<AclEntryRequest> requests) {
        requests.forEach(r -> grant(r.actorId(), r.resourceId(), r.action(), r.expiresAt()));
    }

    @Override
    @Transactional
    public void denyBatch(Collection<AclEntryRequest> requests) {
        requests.forEach(r -> deny(r.actorId(), r.resourceId(), r.action(), r.expiresAt()));
    }

    @Override
    @Transactional
    public void revoke(String actorId, ResourceId resourceId, AclAction action) {
        removeEntry(actorId, resourceId, action, "ALLOW", "REVOKE");
    }

    @Override
    @Transactional
    public void removeDeny(String actorId, ResourceId resourceId, AclAction action) {
        removeEntry(actorId, resourceId, action, "DENY", "REVOKE_DENY");
    }

    @Override
    @Transactional
    public void revokeBatch(Collection<AclEntryRequest> requests) {
        requests.forEach(r -> revoke(r.actorId(), r.resourceId(), r.action()));
    }

    @Override
    @Transactional
    public void removeDenyBatch(Collection<AclEntryRequest> requests) {
        requests.forEach(r -> removeDeny(r.actorId(), r.resourceId(), r.action()));
    }

    @Override
    @Transactional
    public void revokeAll(String actorId, ResourceId resourceId) {
        Instant now = Instant.now();
        String tenancyId = principal.tenancyId();
        String resIdStr = resourceId.toString();
        List<AclEntryEntity> entries = entryRepo.findByActorIdAndResourceIdAndTenancyId(
                actorId, resIdStr, tenancyId);
        for (AclEntryEntity entry : entries) {
            AclAuditLogEntity log = new AclAuditLogEntity();
            log.actorId = actorId;
            log.resourceId = resIdStr;
            log.action = entry.action;
            log.operation = "ALLOW".equals(entry.entryType) ? "REVOKE" : "REVOKE_DENY";
            log.performedBy = principal.actorId();
            log.performedAt = now;
            log.tenancyId = tenancyId;
            auditRepo.save(log);
        }
        entryRepo.deleteByActorAndResource(actorId, resIdStr, tenancyId);
    }

    @Override
    @Transactional
    public void registerParent(ResourceId childResourceId, ResourceId parentResourceId) {
        String tenancyId = principal.tenancyId();
        String childStr = childResourceId.toString();
        ResourceParentKey key = new ResourceParentKey(childStr, tenancyId);
        ResourceParentEntity existing = parentRepo.findById(key).orElse(null);
        if (existing == null) {
            ResourceParentEntity rp = new ResourceParentEntity();
            rp.childResourceId = childStr;
            rp.parentResourceId = parentResourceId.toString();
            rp.tenancyId = tenancyId;
            parentRepo.save(rp);
        } else {
            existing.parentResourceId = parentResourceId.toString();
            parentRepo.save(existing);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResourceId> accessibleResources(String actorId, String resourceType, AclAction action) {
        Set<String> candidates = buildCandidateSet(actorId);
        String prefix = escapeForLike(resourceType) + ":%";
        List<String> satisfyingActions = action.satisfiedBy().stream().map(Enum::name).toList();
        List<String> deniedByActions = action.deniedBy().stream().map(Enum::name).toList();

        List<String> granted;
        if (shouldFilterByTenant()) {
            granted = entryRepo.findGrantedResourcesTenant(
                    satisfyingActions, Instant.now(), candidates, prefix, principal.tenancyId());
        } else {
            granted = entryRepo.findGrantedResources(
                    satisfyingActions, Instant.now(), candidates, prefix);
        }

        Set<String> denied = fetchDeniedResources(candidates, deniedByActions, prefix);
        LinkedHashSet<String> result = new LinkedHashSet<>(granted);
        result.removeAll(denied);
        return result.stream().map(ResourceId::parse).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    @Override
    @Transactional(readOnly = true)
    public AclPage accessibleResources(AclQuery query) {
        Set<String> candidates = buildCandidateSet(query.actorId());
        String prefix = escapeForLike(query.resourceType()) + ":%";
        List<String> satisfyingActions = query.action().satisfiedBy().stream().map(Enum::name).toList();
        List<String> deniedByActions = query.action().deniedBy().stream().map(Enum::name).toList();
        int fetchLimit = query.limit() + 1;
        var pageable = PageRequest.of(0, fetchLimit);

        Set<String> denied = fetchDeniedResources(candidates, deniedByActions, prefix);

        List<String> results;
        if (query.cursor() != null) {
            if (shouldFilterByTenant()) {
                results = entryRepo.findGrantedResourcesTenantCursor(
                        satisfyingActions, Instant.now(), candidates, prefix,
                        principal.tenancyId(), query.cursor(), pageable);
            } else {
                results = entryRepo.findGrantedResourcesCursor(
                        satisfyingActions, Instant.now(), candidates, prefix,
                        query.cursor(), pageable);
            }
        } else {
            if (shouldFilterByTenant()) {
                results = entryRepo.findGrantedResourcesTenantOrdered(
                        satisfyingActions, Instant.now(), candidates, prefix,
                        principal.tenancyId(), pageable);
            } else {
                results = entryRepo.findGrantedResourcesOrdered(
                        satisfyingActions, Instant.now(), candidates, prefix, pageable);
            }
        }

        results = new ArrayList<>(results);
        results.removeAll(denied);

        if (results.size() > query.limit()) {
            List<String> page = results.subList(0, query.limit());
            return new AclPage(page.stream().map(ResourceId::parse).toList(), page.getLast());
        }
        return new AclPage(results.stream().map(ResourceId::parse).toList(), null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResourceId> accessibleResourcesIncludingInherited(
            String actorId, String resourceType, AclAction action) {
        // Recursive CTE — PostgreSQL-only
        return accessibleResources(actorId, resourceType, action);
    }

    private void upsertEntry(String actorId, ResourceId resourceId, AclAction action,
                             Instant expires, String entryType, String auditOp) {
        Instant now = Instant.now();
        String tenancyId = principal.tenancyId();
        String resIdStr = resourceId.toString();

        List<AclEntryEntity> existing = entryRepo.findByActorResourceActionType(
                actorId, resIdStr, action.name(), tenancyId, entryType);
        if (!existing.isEmpty()) {
            AclEntryEntity entry = existing.getFirst();
            entry.expiresAt = expires;
            entry.grantedAt = now;
            entryRepo.save(entry);
        } else {
            AclEntryEntity entry = new AclEntryEntity();
            entry.actorId = actorId;
            entry.resourceId = resIdStr;
            entry.action = action.name();
            entry.entryType = entryType;
            entry.grantedAt = now;
            entry.expiresAt = expires;
            entry.tenancyId = tenancyId;
            entryRepo.save(entry);
        }

        AclAuditLogEntity log = new AclAuditLogEntity();
        log.actorId = actorId;
        log.resourceId = resIdStr;
        log.action = action.name();
        log.operation = auditOp;
        log.performedBy = principal.actorId();
        log.performedAt = now;
        log.expiresAt = expires;
        log.tenancyId = tenancyId;
        auditRepo.save(log);
    }

    private void removeEntry(String actorId, ResourceId resourceId, AclAction action,
                             String entryType, String auditOp) {
        String tenancyId = principal.tenancyId();
        String resIdStr = resourceId.toString();
        int count = entryRepo.deleteByActorResourceActionType(
                actorId, resIdStr, action.name(), tenancyId, entryType);
        if (count > 0) {
            AclAuditLogEntity log = new AclAuditLogEntity();
            log.actorId = actorId;
            log.resourceId = resIdStr;
            log.action = action.name();
            log.operation = auditOp;
            log.performedBy = principal.actorId();
            log.performedAt = Instant.now();
            log.tenancyId = tenancyId;
            auditRepo.save(log);
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

    private boolean resolveAccess(Set<String> candidates, ResourceId resourceId,
                                  AclAction action, int depth) {
        if (depth > 20) return false;

        int resolution = resolveAt(candidates, resourceId, action);
        if (resolution != 0) return resolution > 0;

        ResourceParentEntity parent = parentRepo.findById(
                new ResourceParentKey(resourceId.toString(), principal.tenancyId())).orElse(null);
        if (parent != null) {
            return resolveAccess(candidates, ResourceId.parse(parent.parentResourceId), action, depth + 1);
        }
        return false;
    }

    private int resolveAt(Set<String> candidates, ResourceId resourceId, AclAction action) {
        List<String> deniedByActions = action.deniedBy().stream().map(Enum::name).toList();
        List<String> satisfyingActions = action.satisfiedBy().stream().map(Enum::name).toList();
        String resIdStr = resourceId.toString();

        if (hasEntry(candidates, resIdStr, deniedByActions, "DENY")) return -1;
        if (hasEntry(candidates, resIdStr, satisfyingActions, "ALLOW")) return 1;

        String wildcardStr = new ResourceId(resourceId.type(), "*").toString();
        if (!wildcardStr.equals(resIdStr)) {
            if (hasEntry(candidates, wildcardStr, deniedByActions, "DENY")) return -1;
            if (hasEntry(candidates, wildcardStr, satisfyingActions, "ALLOW")) return 1;
        }

        return 0;
    }

    private boolean hasEntry(Set<String> candidates, String resourceId,
                             List<String> actions, String entryType) {
        long count;
        if (shouldFilterByTenant()) {
            count = entryRepo.countActiveByActorsResourceActionsTypeTenant(
                    candidates, resourceId, actions, entryType, Instant.now(), principal.tenancyId());
        } else {
            count = entryRepo.countActiveByActorsResourceActionsType(
                    candidates, resourceId, actions, entryType, Instant.now());
        }
        return count > 0;
    }

    private Set<String> fetchDeniedResources(Set<String> candidates, List<String> deniedByActions, String prefix) {
        List<String> deniedList;
        if (shouldFilterByTenant()) {
            deniedList = entryRepo.findDeniedResourcesTenant(
                    deniedByActions, Instant.now(), candidates, prefix, principal.tenancyId());
        } else {
            deniedList = entryRepo.findDeniedResources(
                    deniedByActions, Instant.now(), candidates, prefix);
        }
        return new HashSet<>(deniedList);
    }

    private boolean shouldFilterByTenant() {
        return !principal.isCrossTenantAdmin();
    }

    private static String escapeForLike(String resourceType) {
        return resourceType.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
