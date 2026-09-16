package io.casehub.platform.acl.jpa;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AccessControlProviderContractTest;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.ResourceId;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class JpaAccessControlProviderTest extends AccessControlProviderContractTest {

    @Inject
    JpaAccessControlProvider jpaProvider;

    @Inject
    TestGroupMembershipProvider testGroupMembership;

    @Inject
    TestDataCleaner cleaner;
    @Inject
    TestCurrentPrincipal testCurrentPrincipal;

    @Inject
    EntityManager entityManager;


    @Override
    protected AccessControlProvider provider() {
        return jpaProvider;
    }

    @Override
    protected GroupMembershipProvider groupMembership() {
        return testGroupMembership;
    }

    @Override
    protected String tenancyId() {
        return "test-tenant";
    }

    @Override
    protected void setTenancyId(String tenancyId) {
        testCurrentPrincipal.setTenancyId(tenancyId);
    }


    @Override
    protected void clearState() {
        testCurrentPrincipal.setTenancyId("test-tenant");
        cleaner.deleteAll();}

    @Test
    void grant_createsAuditLogEntry() {
        jpaProvider.grant("actor1", ResourceId.parse("case:abc"), AclAction.READ, null);

        List<AclAuditLogEntity> logs = entityManager.createQuery(
                "from AclAuditLogEntity where actorId = ?1", AclAuditLogEntity.class)
                .setParameter(1, "actor1").getResultList();

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
        jpaProvider.grant("actor1", ResourceId.parse("case:abc"), AclAction.WRITE, expires);

        List<AclAuditLogEntity> logs = entityManager.createQuery(
                "from AclAuditLogEntity where actorId = ?1", AclAuditLogEntity.class)
                .setParameter(1, "actor1").getResultList();

        assertEquals(1, logs.size());
        assertNotNull(logs.getFirst().expiresAt);
    }

    @Test
    void revoke_createsAuditLogEntry() {
        jpaProvider.grant("actor1", ResourceId.parse("case:abc"), AclAction.READ, null);
        jpaProvider.revoke("actor1", ResourceId.parse("case:abc"), AclAction.READ);

        List<AclAuditLogEntity> logs = entityManager.createQuery(
                "from AclAuditLogEntity where actorId = ?1 and operation = ?2", AclAuditLogEntity.class)
                .setParameter(1, "actor1").setParameter(2, "REVOKE").getResultList();

        assertEquals(1, logs.size());
        AclAuditLogEntity log = logs.getFirst();
        assertEquals("REVOKE", log.operation);
        assertEquals("case:abc", log.resourceId);
        assertEquals("READ", log.action);
        assertEquals("system", log.performedBy);
    }

    @Test
    void grant_andRevoke_createsTwoAuditLogEntries() {
        jpaProvider.grant("actor1", ResourceId.parse("case:abc"), AclAction.READ, null);
        jpaProvider.revoke("actor1", ResourceId.parse("case:abc"), AclAction.READ);

        long total = entityManager.createQuery(
                "select count(e) from AclAuditLogEntity e where e.actorId = ?1", Long.class)
                .setParameter(1, "actor1").getSingleResult();

        assertEquals(2, total);
    }

    @Test
    void revokeAll_createsAuditLogEntryPerAction() {
        jpaProvider.grant("actor1", ResourceId.parse("case:abc"), AclAction.READ, null);
        jpaProvider.grant("actor1", ResourceId.parse("case:abc"), AclAction.WRITE, null);
        jpaProvider.revokeAll("actor1", ResourceId.parse("case:abc"));

        List<AclAuditLogEntity> revokeLogs = entityManager.createQuery(
                "from AclAuditLogEntity where actorId = ?1 and operation = ?2", AclAuditLogEntity.class)
                .setParameter(1, "actor1").setParameter(2, "REVOKE").getResultList();

        assertEquals(2, revokeLogs.size());
        List<String> actions = revokeLogs.stream().map(l -> l.action).sorted().toList();
        assertEquals(List.of("READ", "WRITE"), actions);
    }

    @Test
    void revokeAll_noGrants_createsNoAuditLog() {
        jpaProvider.revokeAll("actor1", ResourceId.parse("case:abc"));

        long count = entityManager.createQuery(
                "select count(e) from AclAuditLogEntity e where e.actorId = ?1", Long.class)
                .setParameter(1, "actor1").getSingleResult();

        assertEquals(0, count);
    }

    @Test
    void grant_duplicate_createsTwoAuditLogEntries() {
        jpaProvider.grant("actor1", ResourceId.parse("case:abc"), AclAction.READ, null);
        jpaProvider.grant("actor1", ResourceId.parse("case:abc"), AclAction.READ, null);

        long grantCount = entityManager.createQuery(
                "select count(e) from AclAuditLogEntity e where e.actorId = ?1 and e.operation = ?2", Long.class)
                .setParameter(1, "actor1").setParameter(2, "GRANT").getSingleResult();

        assertEquals(2, grantCount);
    }

    @Test
    @Transactional
    void condition_persistedOnEntry() {
        AclEntryEntity entry = new AclEntryEntity();
        entry.actorId    = "actor1";
        entry.resourceId = "case:abc";
        entry.action     = "READ";
        entry.condition  = "status == 'RUNNING'";
        entry.grantedAt  = Instant.now();
        entry.tenancyId  = "";
        entityManager.persist(entry);

        AclEntryEntity found = entityManager.createQuery(
                "from AclEntryEntity where actorId = ?1 and resourceId = ?2", AclEntryEntity.class)
                .setParameter(1, "actor1").setParameter(2, "case:abc")
                .getResultList().stream().findFirst().orElse(null);

        assertNotNull(found);
        assertEquals("status == 'RUNNING'", found.condition);
    }

    @Test
    void condition_nullByDefault() {
        jpaProvider.grant("actor1", ResourceId.parse("case:abc"), AclAction.READ, null);

        AclEntryEntity found = entityManager.createQuery(
                "from AclEntryEntity where actorId = ?1 and resourceId = ?2", AclEntryEntity.class)
                .setParameter(1, "actor1").setParameter(2, "case:abc")
                .getResultList().stream().findFirst().orElse(null);

        assertNotNull(found);
        assertNull(found.condition);
    }

    @Test
    void auditLog_tenancyIdFromPrincipal() {
        jpaProvider.grant("actor1", ResourceId.parse("case:abc"), AclAction.READ, null);

        AclAuditLogEntity log = entityManager.createQuery(
                "from AclAuditLogEntity where actorId = ?1", AclAuditLogEntity.class)
                .setParameter(1, "actor1")
                .getResultList().stream().findFirst().orElse(null);

        assertNotNull(log);
        assertNotNull(log.tenancyId);
        assertFalse(log.tenancyId.isEmpty());
    }
}
