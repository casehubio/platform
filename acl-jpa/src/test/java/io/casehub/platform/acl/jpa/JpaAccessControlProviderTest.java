package io.casehub.platform.acl.jpa;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AccessControlProviderContractTest;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JpaAccessControlProviderTest extends AccessControlProviderContractTest {

    @Inject
    JpaAccessControlProvider jpaProvider;

    @Inject
    TestGroupMembershipProvider testGroupMembership;

    @Inject
    Vertx vertx;

    @Override
    protected AccessControlProvider provider() {
        return jpaProvider;
    }

    @Override
    protected GroupMembershipProvider groupMembership() {
        return testGroupMembership;
    }

    @Override
    protected void clearState() {
        reactive(() -> Panache.withTransaction(() ->
                AclAuditLogEntity.deleteAll()
                        .chain(() -> AclEntryEntity.deleteAll())
                        .chain(() -> ResourceParentEntity.deleteAll())
        ));
    }

    @Test
    void grant_createsAuditLogEntry() {
        jpaProvider.grant("actor1", "case:abc", AclAction.READ, null).toCompletableFuture().join();

        List<AclAuditLogEntity> logs = reactive(() -> Panache.withSession(() ->
                AclAuditLogEntity.<AclAuditLogEntity>list("actorId", "actor1")
        ));

        assertEquals(1, logs.size());
        AclAuditLogEntity log = logs.getFirst();
        assertEquals("actor1", log.actorId);
        assertEquals("case:abc", log.resourceId);
        assertEquals("READ", log.action);
        assertEquals("GRANT", log.operation);
        assertEquals("system", log.performedBy);
        assertNotNull(log.performedAt);
        assertNull(log.expiresAt);
    }

    @Test
    void grant_withExpiry_recordsExpiresAtInAuditLog() {
        Instant expires = Instant.now().plus(1, ChronoUnit.HOURS);
        jpaProvider.grant("actor1", "case:abc", AclAction.WRITE, expires).toCompletableFuture().join();

        List<AclAuditLogEntity> logs = reactive(() -> Panache.withSession(() ->
                AclAuditLogEntity.<AclAuditLogEntity>list("actorId", "actor1")
        ));

        assertEquals(1, logs.size());
        assertNotNull(logs.getFirst().expiresAt);
    }

    @Test
    void revoke_createsAuditLogEntry() {
        jpaProvider.grant("actor1", "case:abc", AclAction.READ, null).toCompletableFuture().join();
        jpaProvider.revoke("actor1", "case:abc", AclAction.READ).toCompletableFuture().join();

        List<AclAuditLogEntity> logs = reactive(() -> Panache.withSession(() ->
                AclAuditLogEntity.<AclAuditLogEntity>list(
                        "actorId = ?1 and operation = ?2", "actor1", "REVOKE")
        ));

        assertEquals(1, logs.size());
        AclAuditLogEntity log = logs.getFirst();
        assertEquals("REVOKE", log.operation);
        assertEquals("case:abc", log.resourceId);
        assertEquals("READ", log.action);
        assertEquals("system", log.performedBy);
    }

    @Test
    void grant_andRevoke_createsTwoAuditLogEntries() {
        jpaProvider.grant("actor1", "case:abc", AclAction.READ, null).toCompletableFuture().join();
        jpaProvider.revoke("actor1", "case:abc", AclAction.READ).toCompletableFuture().join();

        long total = reactive(() -> Panache.withSession(() ->
                AclAuditLogEntity.count("actorId", "actor1")
        ));

        assertEquals(2, total);
    }

    @Test
    void revokeAll_createsAuditLogEntryPerAction() {
        jpaProvider.grant("actor1", "case:abc", AclAction.READ, null).toCompletableFuture().join();
        jpaProvider.grant("actor1", "case:abc", AclAction.WRITE, null).toCompletableFuture().join();
        jpaProvider.revokeAll("actor1", "case:abc").toCompletableFuture().join();

        List<AclAuditLogEntity> revokeLogs = reactive(() -> Panache.withSession(() ->
                AclAuditLogEntity.<AclAuditLogEntity>list(
                        "actorId = ?1 and operation = ?2", "actor1", "REVOKE")
        ));

        assertEquals(2, revokeLogs.size());
        List<String> actions = revokeLogs.stream().map(l -> l.action).sorted().toList();
        assertEquals(List.of("READ", "WRITE"), actions);
    }

    @Test
    void revokeAll_noGrants_createsNoAuditLog() {
        jpaProvider.revokeAll("actor1", "case:abc").toCompletableFuture().join();

        long count = reactive(() -> Panache.withSession(() ->
                AclAuditLogEntity.count("actorId", "actor1")
        ));

        assertEquals(0, count);
    }

    @Test
    void grant_duplicate_createsTwoAuditLogEntries() {
        jpaProvider.grant("actor1", "case:abc", AclAction.READ, null).toCompletableFuture().join();
        jpaProvider.grant("actor1", "case:abc", AclAction.READ, null).toCompletableFuture().join();

        long grantCount = reactive(() -> Panache.withSession(() ->
                AclAuditLogEntity.count(
                        "actorId = ?1 and operation = ?2", "actor1", "GRANT")
        ));

        assertEquals(2, grantCount);
    }

    @Test
    void condition_persistedOnEntry() {
        reactive(() -> Panache.withTransaction(() -> {
            AclEntryEntity entry = new AclEntryEntity();
            entry.actorId = "actor1";
            entry.resourceId = "case:abc";
            entry.action = "READ";
            entry.condition = "status == 'RUNNING'";
            entry.grantedAt = Instant.now();
            entry.tenancyId = "";
            return entry.persist();
        }));

        AclEntryEntity found = reactive(() -> Panache.withSession(() ->
                AclEntryEntity.<AclEntryEntity>find(
                                "actorId = ?1 and resourceId = ?2", "actor1", "case:abc")
                        .firstResult()
        ));

        assertNotNull(found);
        assertEquals("status == 'RUNNING'", found.condition);
    }

    @Test
    void condition_nullByDefault() {
        jpaProvider.grant("actor1", "case:abc", AclAction.READ, null).toCompletableFuture().join();

        AclEntryEntity found = reactive(() -> Panache.withSession(() ->
                AclEntryEntity.<AclEntryEntity>find(
                                "actorId = ?1 and resourceId = ?2", "actor1", "case:abc")
                        .firstResult()
        ));

        assertNotNull(found);
        assertNull(found.condition);
    }

    @Test
    void auditLog_tenancyIdFromPrincipal() {
        jpaProvider.grant("actor1", "case:abc", AclAction.READ, null).toCompletableFuture().join();

        AclAuditLogEntity log = reactive(() -> Panache.withSession(() ->
                AclAuditLogEntity.<AclAuditLogEntity>find("actorId", "actor1")
                        .firstResult()
        ));

        assertNotNull(log);
        assertNotNull(log.tenancyId);
        assertFalse(log.tenancyId.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private <T> T reactive(Supplier<Uni<? extends T>> work) {
        Context context = VertxContext.getOrCreateDuplicatedContext(vertx);
        VertxContextSafetyToggle.setContextSafe(context, true);
        return (T) Uni.createFrom().deferred(work)
                .runSubscriptionOn(r -> context.runOnContext(v -> r.run()))
                .await().indefinitely();
    }
}
