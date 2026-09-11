package io.casehub.platform.memory.sqlite;

import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.casehub.neocortex.memory.*;
import io.casehub.neocortex.memory.testing.CaseMemoryStoreContractTest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@ActivateRequestContext
class SqliteMemoryStoreTest extends CaseMemoryStoreContractTest {

    @Inject SqliteMemoryStore sqliteStore;
    @Inject FixedCurrentPrincipal principal;

    @BeforeEach
    void setup() {
        principal.setTenancyId(TENANT);
        principal.setCrossTenantAdmin(false);
    }

    @AfterEach
    void cleanUp() {
        sqliteStore.deleteAll();
        principal.setTenancyId(TENANT);
        principal.setCrossTenantAdmin(false);
    }

    @Override
    protected CaseMemoryStore store() {
        return sqliteStore;
    }

    @Test
    void eraseEntityAcrossTenants_deletes_across_tenants() {
        // Seed under TENANT
        store().store(MemoryInput.of("entity-1", DOMAIN, TENANT, "data-a"));
        // Seed under OTHER_TENANT
        principal.setTenancyId(OTHER_TENANT);
        store().store(MemoryInput.of("entity-1", DOMAIN, OTHER_TENANT, "data-b"));
        // Erase as cross-tenant admin
        principal.setTenancyId(TENANT);
        principal.setCrossTenantAdmin(true);
        int count = sqliteStore.eraseEntityAcrossTenants("entity-1", Set.of(TENANT, OTHER_TENANT));
        assertEquals(2, count);
    }

    @Test
    void eraseEntityAcrossTenants_requires_cross_tenant_admin() {
        principal.setCrossTenantAdmin(false);
        assertThrows(SecurityException.class,
            () -> sqliteStore.eraseEntityAcrossTenants("entity-1", Set.of(TENANT)));
    }

    // --- SQLite-specific tests ---

    @Test
    void queryWithRelevanceOrderUsesFts5() {
        store().store(MemoryInput.of("entity-1", DOMAIN, TENANT,
            "the patient reported ibuprofen side effects including nausea"));
        store().store(MemoryInput.of("entity-1", DOMAIN, TENANT,
            "appointment scheduled for next tuesday"));

        var results = store().query(
            MemoryQuery.forEntity("entity-1", DOMAIN, TENANT)
                .withOrder(MemoryOrder.RELEVANCE)
                .withQuestion("ibuprofen side effects"));

        assertFalse(results.isEmpty());
        assertTrue(results.get(0).text().contains("ibuprofen"),
            "Expected ibuprofen memory first; got: " + results.get(0).text());
    }

    @Test
    void queryWithRelevanceOrderNullQuestion() {
        store().store(MemoryInput.of("entity-1", DOMAIN, TENANT, "alpha"));
        store().store(MemoryInput.of("entity-1", DOMAIN, TENANT, "beta"));

        var results = store().query(
            MemoryQuery.forEntity("entity-1", DOMAIN, TENANT)
                .withOrder(MemoryOrder.RELEVANCE)
                .withQuestion(null));

        // null question → chronological fallback regardless of fts.enabled
        assertEquals(2, results.size());
        assertEquals("beta", results.get(0).text());
    }

    @Test
    void storeAllWrapsInSingleTransaction() {
        var inputs = List.of(
            MemoryInput.of("entity-1", DOMAIN, TENANT, "batch-a"),
            MemoryInput.of("entity-1", DOMAIN, TENANT, "batch-b"),
            MemoryInput.of("entity-1", DOMAIN, TENANT, "batch-c")
        );
        var result = store().storeAll(inputs);

        assertTrue(result.allSucceeded());
        assertEquals(3, result.stored().size());
        var stored = store().query(MemoryQuery.forEntity("entity-1", DOMAIN, TENANT));
        assertEquals(3, stored.size());
        assertTrue(stored.stream().anyMatch(m -> "batch-a".equals(m.text())));
        assertTrue(stored.stream().anyMatch(m -> "batch-b".equals(m.text())));
        assertTrue(stored.stream().anyMatch(m -> "batch-c".equals(m.text())));
    }

    @Test
    void ftsOperatorCharactersInQuestionAreStripped() {
        store().store(MemoryInput.of("entity-1", DOMAIN, TENANT,
            "pre-trial hearing was held yesterday"));

        // "pre-trial" with '-' stripped becomes "pre trial" — both words ANDed, matches
        var results = store().query(
            MemoryQuery.forEntity("entity-1", DOMAIN, TENANT)
                .withOrder(MemoryOrder.RELEVANCE)
                .withQuestion("pre-trial"));

        assertEquals(1, results.size());
    }

    public static class FtsDisabledProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Map.of("casehub.memory.sqlite.fts.enabled", "false");
        }
    }
}
