package io.casehub.platform.testing.memory;

import io.casehub.platform.api.memory.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adapter-agnostic contract test suite for CaseMemoryStore implementations.
 * Extend this class and implement {@link #store()} to plug in any adapter.
 * Cleanup between tests is the subclass's responsibility (@AfterEach or @TestTransaction).
 *
 * <p>The {@link #store()} method must return the same adapter instance for the entire
 * duration of each test method. Subclasses backed by a stateful in-memory adapter must
 * initialize the instance in {@code @BeforeEach} and return it from {@link #store()}.
 * CDI-injected adapters satisfy this naturally.
 */
public abstract class CaseMemoryStoreContractTest {

    protected static final String TENANT       = "tenant-1";
    protected static final String OTHER_TENANT = "tenant-2";
    protected static final MemoryDomain DOMAIN       = new MemoryDomain("d");
    protected static final MemoryDomain OTHER_DOMAIN = new MemoryDomain("other");

    protected abstract CaseMemoryStore store();

    protected MemoryInput input(String text) {
        return new MemoryInput("entity-1", DOMAIN, TENANT, null, text, Map.of());
    }

    protected MemoryInput input(String entityId, String text) {
        return new MemoryInput(entityId, DOMAIN, TENANT, null, text, Map.of());
    }

    protected MemoryInput inputWithCase(String text, String caseId) {
        return new MemoryInput("entity-1", DOMAIN, TENANT, caseId, text, Map.of());
    }

    protected MemoryQuery query() {
        return MemoryQuery.forEntity("entity-1", DOMAIN, TENANT);
    }

    protected EraseRequest eraseRequest() {
        return new EraseRequest("entity-1", DOMAIN, TENANT, null);
    }

    // --- store ---

    @Test
    void store_assigns_non_empty_memory_id() {
        assertFalse(store().store(input("hello")).isEmpty());
    }

    @Test
    void store_assigns_unique_ids() {
        assertNotEquals(store().store(input("a")), store().store(input("b")));
    }

    @Test
    void store_tenant_mismatch_throws() {
        var bad = new MemoryInput("entity-1", DOMAIN, OTHER_TENANT, null, "x", Map.of());
        assertThrows(SecurityException.class, () -> store().store(bad));
    }

    // --- query single entity ---

    @Test
    void query_empty_when_nothing_stored() {
        assertTrue(store().query(query()).isEmpty());
    }

    @Test
    void query_does_not_leak_across_tenants() {
        store().store(input("secret"));
        var crossQuery = MemoryQuery.forEntity("entity-1", DOMAIN, OTHER_TENANT);
        assertThrows(SecurityException.class, () -> store().query(crossQuery));
    }

    @Test
    void query_does_not_leak_across_domains() {
        store().store(input("finance fact"));
        assertTrue(store().query(MemoryQuery.forEntity("entity-1", OTHER_DOMAIN, TENANT)).isEmpty());
    }

    @Test
    void query_with_caseId_filters_correctly() {
        store().store(input("no case"));
        store().store(inputWithCase("case A", "case-1"));
        store().store(inputWithCase("case B", "case-2"));
        var results = store().query(query().withCaseId("case-1"));
        assertEquals(1, results.size());
        assertEquals("case A", results.get(0).text());
    }

    @Test
    void query_null_caseId_returns_all() {
        store().store(input("no case"));
        store().store(inputWithCase("with case", "case-1"));
        assertEquals(2, store().query(query()).size());
    }

    @Test
    void query_with_since_excludes_older_memories() throws InterruptedException {
        store().store(input("old"));
        Thread.sleep(5);      // ensure "old" timestamp < barrier (since uses >=)
        Instant barrier = Instant.now();
        Thread.sleep(5);
        store().store(input("new"));
        var results = store().query(query().withSince(barrier));
        assertEquals(1, results.size());
        assertEquals("new", results.get(0).text());
    }

    @Test
    void query_limit_is_honoured() {
        for (int i = 0; i < 5; i++) store().store(input("item " + i));
        assertEquals(3, store().query(query().withLimit(3)).size());
    }

    // --- MemoryOrder ---

    @Test
    void chronological_order_is_default() {
        store().store(input("first"));
        store().store(input("second"));
        var results = store().query(query().withOrder(MemoryOrder.CHRONOLOGICAL));
        assertEquals("second", results.get(0).text());
        assertEquals("first",  results.get(1).text());
    }

    @Test
    void relevance_order_without_question_falls_back_to_chronological() {
        store().store(input("first"));
        store().store(input("second"));
        var results = store().query(query().withOrder(MemoryOrder.RELEVANCE));
        assertEquals("second", results.get(0).text());
    }

    // --- multi-entity query ---

    @Test
    void multi_entity_query_returns_facts_from_all_entities() {
        store().store(input("entity-1", "fact about e1"));
        store().store(input("entity-2", "fact about e2"));
        var results = store().query(
            MemoryQuery.forEntities(List.of("entity-1", "entity-2"), DOMAIN, TENANT));
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(m -> "fact about e1".equals(m.text())));
        assertTrue(results.stream().anyMatch(m -> "fact about e2".equals(m.text())));
    }

    @Test
    void multi_entity_query_limit_applies_to_combined_result_set() {
        for (int i = 0; i < 5; i++) store().store(input("entity-1", "e1-" + i));
        for (int i = 0; i < 5; i++) store().store(input("entity-2", "e2-" + i));
        var results = store().query(
            MemoryQuery.forEntities(List.of("entity-1", "entity-2"), DOMAIN, TENANT)
                .withLimit(6));
        assertEquals(6, results.size());
    }

    @Test
    void multi_entity_result_carries_entityId_per_memory() {
        store().store(input("entity-1", "fact about e1"));
        store().store(input("entity-2", "fact about e2"));
        var results = store().query(
            MemoryQuery.forEntities(List.of("entity-1", "entity-2"), DOMAIN, TENANT));
        assertTrue(results.stream().anyMatch(m -> "entity-1".equals(m.entityId())));
        assertTrue(results.stream().anyMatch(m -> "entity-2".equals(m.entityId())));
    }

    // --- MemoryAttributeKeys round-trip ---

    @Test
    void attribute_keys_round_trip_correctly() {
        var attrs = Map.of(
            MemoryAttributeKeys.ACTOR_ID,   "actor-123",
            MemoryAttributeKeys.OUTCOME,    "DONE",
            MemoryAttributeKeys.CONFIDENCE, MemoryAttributeKeys.formatConfidence(0.87)
        );
        store().store(new MemoryInput("entity-1", DOMAIN, TENANT, null, "reviewer completed task", attrs));
        var results = store().query(query());
        assertEquals(1, results.size());
        var stored = results.get(0).attributes();
        assertEquals("actor-123", stored.get(MemoryAttributeKeys.ACTOR_ID));
        assertEquals("DONE", stored.get(MemoryAttributeKeys.OUTCOME));
        assertEquals(0.87,
            MemoryAttributeKeys.parseConfidence(stored.get(MemoryAttributeKeys.CONFIDENCE)), 0.0001);
    }

    // --- erase ---

    @Test
    void erase_removes_matching_memories() {
        store().store(input("to erase"));
        store().erase(eraseRequest());
        assertTrue(store().query(query()).isEmpty());
    }

    @Test
    void erase_with_caseId_leaves_other_cases() {
        store().store(input("no case"));
        store().store(inputWithCase("case A", "case-1"));
        store().store(inputWithCase("case B", "case-2"));
        store().erase(new EraseRequest("entity-1", DOMAIN, TENANT, "case-1"));
        var remaining = store().query(query());
        assertEquals(2, remaining.size());
        assertTrue(remaining.stream().anyMatch(m -> "no case".equals(m.text())));
        assertTrue(remaining.stream().anyMatch(m -> "case B".equals(m.text())));
    }

    @Test
    void erase_null_caseId_removes_all_entries_including_case_scoped() {
        store().store(input("no case"));
        store().store(inputWithCase("case A", "case-1"));
        store().store(inputWithCase("case B", "case-2"));
        store().erase(eraseRequest()); // null caseId = erase ALL
        assertTrue(store().query(query()).isEmpty(),
            "erase with null caseId must remove all entries regardless of their caseId");
    }

    @Test
    void erase_tenant_mismatch_throws() {
        assertThrows(SecurityException.class,
            () -> store().erase(new EraseRequest("entity-1", DOMAIN, OTHER_TENANT, null)));
    }

    // --- eraseById ---

    @Test
    void eraseById_removes_specific_memory() {
        String id = store().store(input("to delete"));
        store().store(input("to keep"));
        store().eraseById(id, TENANT);
        var remaining = store().query(query());
        assertEquals(1, remaining.size());
        assertEquals("to keep", remaining.get(0).text());
    }

    @Test
    void eraseById_does_not_cross_tenant_boundary() {
        assertThrows(SecurityException.class, () -> store().eraseById("any-id", OTHER_TENANT));
    }

    // --- eraseEntity ---

    @Test
    void eraseEntity_removes_all_domains_for_entity() {
        store().store(new MemoryInput("entity-1", DOMAIN,       TENANT, null, "finance", Map.of()));
        store().store(new MemoryInput("entity-1", OTHER_DOMAIN, TENANT, null, "health",  Map.of()));
        store().eraseEntity("entity-1", TENANT);
        assertTrue(store().query(query()).isEmpty());
        assertTrue(store().query(MemoryQuery.forEntity("entity-1", OTHER_DOMAIN, TENANT)).isEmpty());
    }

    @Test
    void eraseEntity_does_not_cross_tenant_boundary() {
        assertThrows(SecurityException.class, () -> store().eraseEntity("entity-1", OTHER_TENANT));
    }

    @Test
    void eraseEntity_leaves_other_entities_intact() {
        store().store(new MemoryInput("entity-1", DOMAIN, TENANT, null, "mine",  Map.of()));
        store().store(new MemoryInput("entity-2", DOMAIN, TENANT, null, "other", Map.of()));
        store().eraseEntity("entity-1", TENANT);
        var e2results = store().query(MemoryQuery.forEntity("entity-2", DOMAIN, TENANT));
        assertEquals(1, e2results.size());
        assertEquals("other", e2results.get(0).text());
    }

    // --- storeAll ---

    @Test
    void storeAll_empty_returns_empty_list() {
        assertEquals(List.of(), store().storeAll(List.of()));
    }

    @Test
    void storeAll_returns_non_empty_ids_in_input_order() {
        var a = input("entity-1", "fact-a");
        var b = input("entity-2", "fact-b");

        List<String> ids = store().storeAll(List.of(a, b));

        assertEquals(2, ids.size());
        ids.forEach(id -> assertFalse(id.isEmpty(), "each returned ID must be non-empty"));
        assertNotEquals(ids.get(0), ids.get(1));

        // Verify ordering: ids.get(0) must correspond to input a (entity-1).
        // Erase by the first returned ID — only entity-1's entry must disappear.
        store().eraseById(ids.get(0), TENANT);
        assertTrue(store().query(MemoryQuery.forEntity("entity-1", DOMAIN, TENANT)).isEmpty(),
            "ids.get(0) must be the ID assigned to the first input (entity-1)");
        assertFalse(store().query(MemoryQuery.forEntity("entity-2", DOMAIN, TENANT)).isEmpty(),
            "erasing ids.get(0) must not affect the second input (entity-2)");
    }

    @Test
    void storeAll_all_entries_queryable_after_call() {
        var a = input("entity-1", "fact-a");
        var b = input("entity-2", "fact-b");

        store().storeAll(List.of(a, b));

        var e1 = store().query(MemoryQuery.forEntity("entity-1", DOMAIN, TENANT));
        var e2 = store().query(MemoryQuery.forEntity("entity-2", DOMAIN, TENANT));
        assertEquals(1, e1.size());
        assertEquals("fact-a", e1.get(0).text());
        assertEquals(1, e2.size());
        assertEquals("fact-b", e2.get(0).text());
    }

    @Test
    void storeAll_all_wrong_tenant_throws_security_exception() {
        var bad = new MemoryInput("entity-1", DOMAIN, OTHER_TENANT, null, "x", Map.of());
        assertThrows(SecurityException.class, () -> store().storeAll(List.of(bad)));
    }

    @Test
    void storeAll_second_item_tenant_mismatch_no_entries_stored() {
        var good = input("entity-1", "fact");
        var bad  = new MemoryInput("entity-2", DOMAIN, OTHER_TENANT, null, "x", Map.of());
        assertThrows(SecurityException.class, () -> store().storeAll(List.of(good, bad)));
        assertTrue(store().query(query()).isEmpty(),
            "first item must not be persisted when second item fails tenant check");
    }
}
