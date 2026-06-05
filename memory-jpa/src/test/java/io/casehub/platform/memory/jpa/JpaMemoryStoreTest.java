package io.casehub.platform.memory.jpa;

import io.casehub.platform.api.memory.*;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.casehub.platform.testing.memory.CaseMemoryStoreContractTest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the full CaseMemoryStoreContractTest suite against JpaMemoryStore on H2.
 *
 * <p><strong>Test isolation:</strong> @TestTransaction at class level does not roll back inherited
 * test methods (CDI interception applies only to methods declared in this class, not inherited ones).
 * Instead we use explicit @BeforeEach + @AfterEach in REQUIRES_NEW transactions so each test sees
 * a clean slate regardless of the previous test's outcome.
 *
 * <p><strong>Principal:</strong> FixedCurrentPrincipal (@Alternative @Priority(1)) is active on the
 * test classpath and displaces MockCurrentPrincipal. Its tenancyId defaults to DEFAULT_TENANT_ID;
 * setup() sets it to TENANT ("tenant-1") before each test.
 */
@QuarkusTest
class JpaMemoryStoreTest extends CaseMemoryStoreContractTest {

    @Inject JpaMemoryStore jpaStore;
    @Inject FixedCurrentPrincipal principal;
    @Inject EntityManager em;

    @BeforeEach
    @Transactional(TxType.REQUIRES_NEW)
    void setup() {
        principal.setTenancyId(TENANT);
        em.createQuery("DELETE FROM MemoryEntry").executeUpdate();
    }

    @AfterEach
    @Transactional(TxType.REQUIRES_NEW)
    void cleanup() {
        em.createQuery("DELETE FROM MemoryEntry").executeUpdate();
    }

    @Override
    protected CaseMemoryStore store() {
        return jpaStore;
    }

    // JPA-specific: assertTenant guard fires before any backend call
    @Test
    void assertTenant_mismatch_throws_before_backend_call() {
        var bad = new MemoryInput("entity-1", DOMAIN, OTHER_TENANT, null, "x", Map.of());
        assertThrows(SecurityException.class, () -> store().store(bad));
    }

    @Test
    void storeAll_mixed_tenant_does_not_persist_any_entry() {
        var good = new MemoryInput("entity-1", DOMAIN, TENANT,       null, "good", Map.of());
        var bad  = new MemoryInput("entity-1", DOMAIN, OTHER_TENANT, null, "bad",  Map.of());

        assertThrows(SecurityException.class,
            () -> store().storeAll(List.of(good, bad)));

        // With the single-transaction override: exception fires during stream.toList(),
        // before MemoryEntry.persist() is called → 0 rows.
        // Without the override (SPI default): item 0 committed in its own transaction → 1 row.
        assertEquals(0, store().query(query()).size(),
            "Mixed-tenant storeAll must not persist any entries");
    }
}
