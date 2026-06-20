package io.casehub.platform.acl.jpa;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

@ApplicationScoped
public class JpaAccessControlProvider implements AccessControlProvider {

    @Inject
    GroupMembershipProvider groupMembership;

    @Inject
    CurrentPrincipal principal;

    @Inject
    Vertx vertx;

    @Override
    public CompletionStage<Boolean> canAccess(String actorId, String resourceId, AclAction action) {
        Set<String> candidates = buildCandidateSet(actorId);
        return execute(() -> Panache.withSession(() ->
                canAccessWithCandidates(candidates, resourceId, action, 0)
        ));
    }

    @Override
    public CompletionStage<Void> grant(String actorId, String resourceId, AclAction action, Instant expires) {
        Instant now = Instant.now();

        return execute(() -> Panache.withTransaction(() ->
                AclEntryEntity.<AclEntryEntity>list(
                                "actorId = ?1 and resourceId = ?2 and action = ?3",
                                actorId, resourceId, action.name())
                        .chain(existing -> {
                            if (!existing.isEmpty()) {
                                AclEntryEntity entry = existing.getFirst();
                                entry.expiresAt = expires;
                                entry.grantedAt = now;
                                return entry.persist();
                            } else {
                                AclEntryEntity entry = new AclEntryEntity();
                                entry.actorId = actorId;
                                entry.resourceId = resourceId;
                                entry.action = action.name();
                                entry.grantedAt = now;
                                entry.expiresAt = expires;
                                entry.tenancyId = "";
                                return entry.persist();
                            }
                        })
                        .chain(() -> {
                            AclAuditLogEntity log = new AclAuditLogEntity();
                            log.actorId = actorId;
                            log.resourceId = resourceId;
                            log.action = action.name();
                            log.operation = "GRANT";
                            log.performedBy = principal.actorId();
                            log.performedAt = now;
                            log.expiresAt = expires;
                            log.tenancyId = principal.tenancyId();
                            return log.persist();
                        })
                        .replaceWithVoid()
        ));
    }

    @Override
    public CompletionStage<Void> revoke(String actorId, String resourceId, AclAction action) {
        return execute(() -> Panache.withTransaction(() ->
                AclEntryEntity.delete(
                                "actorId = ?1 and resourceId = ?2 and action = ?3",
                                actorId, resourceId, action.name())
                        .chain(() -> {
                            AclAuditLogEntity log = new AclAuditLogEntity();
                            log.actorId = actorId;
                            log.resourceId = resourceId;
                            log.action = action.name();
                            log.operation = "REVOKE";
                            log.performedBy = principal.actorId();
                            log.performedAt = Instant.now();
                            log.tenancyId = principal.tenancyId();
                            return log.persist();
                        })
                        .replaceWithVoid()
        ));
    }

    @Override
    public CompletionStage<Void> revokeAll(String actorId, String resourceId) {
        return execute(() -> Panache.withTransaction(() ->
                AclEntryEntity.delete("actorId = ?1 and resourceId = ?2", actorId, resourceId)
                        .replaceWithVoid()
        ));
    }

    @Override
    public CompletionStage<Void> registerParent(String childResourceId, String parentResourceId) {
        return execute(() -> Panache.withTransaction(() ->
                ResourceParentEntity.<ResourceParentEntity>findById(childResourceId)
                        .chain(existing -> {
                            if (existing == null) {
                                ResourceParentEntity rp = new ResourceParentEntity();
                                rp.childResourceId = childResourceId;
                                rp.parentResourceId = parentResourceId;
                                rp.tenancyId = "";
                                return rp.persist();
                            }
                            return Uni.createFrom().item(existing);
                        })
                        .replaceWithVoid()
        ));
    }

    @Override
    public CompletionStage<List<String>> accessibleResources(String actorId, String resourceType, AclAction action) {
        Set<String> candidates = buildCandidateSet(actorId);
        String prefix = resourceType + ":%";
        return execute(() -> Panache.withSession(() ->
                AclEntryEntity.find(
                                "select distinct e.resourceId from AclEntryEntity e " +
                                        "where e.action = ?1 " +
                                        "and (e.expiresAt is null or e.expiresAt > ?2) " +
                                        "and e.actorId in ?3 " +
                                        "and e.resourceId like ?4",
                                action.name(), Instant.now(), candidates, prefix)
                        .project(String.class)
                        .list()
        ));
    }

    private Set<String> buildCandidateSet(String actorId) {
        Set<String> candidates = new HashSet<>();
        candidates.add(actorId);
        for (String group : groupMembership.groupsOf(actorId)) {
            candidates.add("group:" + group);
        }
        return candidates;
    }

    private Uni<Boolean> canAccessWithCandidates(Set<String> candidates, String resourceId,
                                                  AclAction action, int depth) {
        if (depth > 20) return Uni.createFrom().item(false);

        return AclEntryEntity.count(
                        "actorId in ?1 and resourceId = ?2 and action = ?3 " +
                                "and (expiresAt is null or expiresAt > ?4)",
                        candidates, resourceId, action.name(), Instant.now())
                .chain(count -> {
                    if (count > 0) return Uni.createFrom().item(true);

                    return ResourceParentEntity.<ResourceParentEntity>list(
                                    "childResourceId", resourceId)
                            .chain(parentList -> {
                                if (!parentList.isEmpty()) {
                                    return canAccessWithCandidates(candidates,
                                            parentList.getFirst().parentResourceId,
                                            action, depth + 1);
                                }
                                return Uni.createFrom().item(false);
                            });
                });
    }

    @SuppressWarnings("unchecked")
    private <T> CompletionStage<T> execute(Supplier<Uni<? extends T>> work) {
        Context context = VertxContext.getOrCreateDuplicatedContext(vertx);
        VertxContextSafetyToggle.setContextSafe(context, true);
        return (CompletionStage<T>) Uni.createFrom().deferred(work)
                .runSubscriptionOn(r -> context.runOnContext(v -> r.run()))
                .subscribeAsCompletionStage();
    }
}
