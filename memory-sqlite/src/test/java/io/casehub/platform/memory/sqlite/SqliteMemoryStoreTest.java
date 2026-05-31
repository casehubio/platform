package io.casehub.platform.memory.sqlite;

import io.casehub.platform.api.memory.*;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.casehub.platform.testing.memory.CaseMemoryStoreContractTest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SqliteMemoryStoreTest extends CaseMemoryStoreContractTest {

    @Inject SqliteMemoryStore sqliteStore;
    @Inject FixedCurrentPrincipal principal;

    @BeforeEach
    void setup() {
        principal.setTenancyId(TENANT);
    }

    @AfterEach
    void cleanUp() {
        // No @TestTransaction (no JTA). Clean known entity IDs after each test.
        sqliteStore.eraseEntity("entity-1", TENANT);
        sqliteStore.eraseEntity("entity-2", TENANT);
    }

    @Override
    protected CaseMemoryStore store() {
        return sqliteStore;
    }

    // --- SQLite-specific tests ---

    @Test
    void queryWithRelevanceOrderUsesFts5() {
        store().store(new MemoryInput("entity-1", DOMAIN, TENANT, null,
            "the patient reported ibuprofen side effects including nausea", Map.of()));
        store().store(new MemoryInput("entity-1", DOMAIN, TENANT, null,
            "appointment scheduled for next tuesday", Map.of()));

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
        store().store(new MemoryInput("entity-1", DOMAIN, TENANT, null, "alpha", Map.of()));
        store().store(new MemoryInput("entity-1", DOMAIN, TENANT, null, "beta", Map.of()));

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
            new MemoryInput("entity-1", DOMAIN, TENANT, null, "batch-a", Map.of()),
            new MemoryInput("entity-1", DOMAIN, TENANT, null, "batch-b", Map.of()),
            new MemoryInput("entity-1", DOMAIN, TENANT, null, "batch-c", Map.of())
        );
        var ids = store().storeAll(inputs);

        assertEquals(3, ids.size());
        var stored = store().query(MemoryQuery.forEntity("entity-1", DOMAIN, TENANT));
        assertEquals(3, stored.size());
        assertTrue(stored.stream().anyMatch(m -> "batch-a".equals(m.text())));
        assertTrue(stored.stream().anyMatch(m -> "batch-b".equals(m.text())));
        assertTrue(stored.stream().anyMatch(m -> "batch-c".equals(m.text())));
    }

    @Test
    void ftsOperatorCharactersInQuestionAreStripped() {
        store().store(new MemoryInput("entity-1", DOMAIN, TENANT, null,
            "pre-trial hearing was held yesterday", Map.of()));

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
