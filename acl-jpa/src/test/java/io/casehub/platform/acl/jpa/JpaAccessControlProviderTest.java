package io.casehub.platform.acl.jpa;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AccessControlProviderContractTest;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
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
        cleaner.deleteAll();
    }

    @Test
    void grant_createsAuditLogEntry() {
        jpaProvider.grant("actor1", "case:abc", AclAction.READ, null);

        List<AclAuditLogEntity> logs = AclAuditLogEntity.list("actorId", "actor1");

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
        jpaProvider.grant("actor1", "case:abc", AclAction.WRITE, expires);

        List<AclAuditLogEntity> logs = AclAuditLogEntity.list("actorId", "actor1");

        assertEquals(1, logs.size());
        assertNotNull(logs.getFirst().expiresAt);
    }

    @Test
    void revoke_createsAuditLogEntry() {
        jpaProvider.grant("actor1", "case:abc", AclAction.READ, null);
        jpaProvider.revoke("actor1", "case:abc", AclAction.READ);

        List<AclAuditLogEntity> logs = AclAuditLogEntity.list(
                "actorId = ?1 and operation = ?2", "actor1", "REVOKE");

        assertEquals(1, logs.size());
        AclAuditLogEntity log = logs.getFirst();
        assertEquals("REVOKE", log.operation);
        assertEquals("case:abc", log.resourceId);
        assertEquals("READ", log.action);
        assertEquals("system", log.performedBy);
    }

    @Test
    void grant_andRevoke_createsTwoAuditLogEntries() {
        jpaProvider.grant("actor1", "case:abc", AclAction.READ, null);
        jpaProvider.revoke("actor1", "case:abc", AclAction.READ);

        long total = AclAuditLogEntity.count("actorId", "actor1");

        assertEquals(2, total);
    }

    @Test
    void revokeAll_createsAuditLogEntryPerAction() {
        jpaProvider.grant("actor1", "case:abc", AclAction.READ, null);
        jpaProvider.grant("actor1", "case:abc", AclAction.WRITE, null);
        jpaProvider.revokeAll("actor1", "case:abc");

        List<AclAuditLogEntity> revokeLogs = AclAuditLogEntity.list(
                "actorId = ?1 and operation = ?2", "actor1", "REVOKE");

        assertEquals(2, revokeLogs.size());
        List<String> actions = revokeLogs.stream().map(l -> l.action).sorted().toList();
        assertEquals(List.of("READ", "WRITE"), actions);
    }

    @Test
    void revokeAll_noGrants_createsNoAuditLog() {
        jpaProvider.revokeAll("actor1", "case:abc");

        long count = AclAuditLogEntity.count("actorId", "actor1");

        assertEquals(0, count);
    }

    @Test
    void grant_duplicate_createsTwoAuditLogEntries() {
        jpaProvider.grant("actor1", "case:abc", AclAction.READ, null);
        jpaProvider.grant("actor1", "case:abc", AclAction.READ, null);

        long grantCount = AclAuditLogEntity.count(
                "actorId = ?1 and operation = ?2", "actor1", "GRANT");

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
        entry.persist();

        AclEntryEntity found = AclEntryEntity.<AclEntryEntity>find(
                                                     "actorId = ?1 and resourceId = ?2", "actor1", "case:abc")
                                             .firstResult();

        assertNotNull(found);
        assertEquals("status == 'RUNNING'", found.condition);
    }

    @Test
    void condition_nullByDefault() {
        jpaProvider.grant("actor1", "case:abc", AclAction.READ, null);

        AclEntryEntity found = AclEntryEntity.<AclEntryEntity>find(
                                                     "actorId = ?1 and resourceId = ?2", "actor1", "case:abc")
                                             .firstResult();

        assertNotNull(found);
        assertNull(found.condition);
    }

    @Test
    void auditLog_tenancyIdFromPrincipal() {
        jpaProvider.grant("actor1", "case:abc", AclAction.READ, null);

        AclAuditLogEntity log = AclAuditLogEntity.<AclAuditLogEntity>find("actorId", "actor1")
                                                 .firstResult();

        assertNotNull(log);
        assertNotNull(log.tenancyId);
        assertFalse(log.tenancyId.isEmpty());
    }
}
