package io.casehub.platform.acl.jpa;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
@Transactional
public class JpaAccessControlProvider implements AccessControlProvider {

    @Inject
    EntityManager em;

    @Inject
    GroupMembershipProvider groupMembership;

    @Override
    public boolean canAccess(String actorId, String resourceId, AclAction action) {
        Set<String> candidates = buildCandidateSet(actorId);
        return canAccessWithCandidates(candidates, resourceId, action, 0);
    }

    @Override
    public void grant(String actorId, String resourceId, AclAction action, Instant expires) {
        List<AclEntryEntity> existing = em.createQuery(
                "SELECT e FROM AclEntryEntity e WHERE e.actorId = :actor " +
                "AND e.resourceId = :resource AND e.action = :action",
                AclEntryEntity.class)
                .setParameter("actor", actorId)
                .setParameter("resource", resourceId)
                .setParameter("action", action.name())
                .getResultList();

        if (!existing.isEmpty()) {
            AclEntryEntity entry = existing.get(0);
            entry.setExpiresAt(expires);
            entry.setGrantedAt(Instant.now());
        } else {
            em.persist(new AclEntryEntity(actorId, resourceId, action.name(),
                    Instant.now(), expires, ""));
        }
    }

    @Override
    public void revoke(String actorId, String resourceId, AclAction action) {
        em.createQuery(
                "DELETE FROM AclEntryEntity e WHERE e.actorId = :actor " +
                "AND e.resourceId = :resource AND e.action = :action")
                .setParameter("actor", actorId)
                .setParameter("resource", resourceId)
                .setParameter("action", action.name())
                .executeUpdate();
    }

    @Override
    public void revokeAll(String actorId, String resourceId) {
        em.createQuery(
                "DELETE FROM AclEntryEntity e WHERE e.actorId = :actor " +
                "AND e.resourceId = :resource")
                .setParameter("actor", actorId)
                .setParameter("resource", resourceId)
                .executeUpdate();
    }

    @Override
    public void registerParent(String childResourceId, String parentResourceId) {
        ResourceParentEntity existing = em.find(ResourceParentEntity.class, childResourceId);
        if (existing == null) {
            em.persist(new ResourceParentEntity(childResourceId, parentResourceId, ""));
        }
    }

    @Override
    public List<String> accessibleResources(String actorId, String resourceType, AclAction action) {
        Set<String> candidates = buildCandidateSet(actorId);
        String prefix = resourceType + ":%";
        return em.createQuery(
                "SELECT DISTINCT e.resourceId FROM AclEntryEntity e " +
                "WHERE e.action = :action " +
                "AND (e.expiresAt IS NULL OR e.expiresAt > :now) " +
                "AND e.actorId IN :candidates " +
                "AND e.resourceId LIKE :prefix",
                String.class)
                .setParameter("action", action.name())
                .setParameter("now", Instant.now())
                .setParameter("candidates", candidates)
                .setParameter("prefix", prefix)
                .getResultList();
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
        if (depth > 20) return false;

        long count = em.createQuery(
                "SELECT COUNT(e) FROM AclEntryEntity e " +
                "WHERE e.actorId IN :candidates " +
                "AND e.resourceId = :resource " +
                "AND e.action = :action " +
                "AND (e.expiresAt IS NULL OR e.expiresAt > :now)",
                Long.class)
                .setParameter("candidates", candidates)
                .setParameter("resource", resourceId)
                .setParameter("action", action.name())
                .setParameter("now", Instant.now())
                .getSingleResult();

        if (count > 0) return true;

        List<ResourceParentEntity> parentList = em.createQuery(
                "SELECT rp FROM ResourceParentEntity rp WHERE rp.childResourceId = :child",
                ResourceParentEntity.class)
                .setParameter("child", resourceId)
                .getResultList();

        if (!parentList.isEmpty()) {
            return canAccessWithCandidates(candidates, parentList.get(0).getParentResourceId(),
                    action, depth + 1);
        }
        return false;
    }
}
