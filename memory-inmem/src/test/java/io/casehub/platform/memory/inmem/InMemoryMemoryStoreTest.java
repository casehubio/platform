package io.casehub.platform.memory.inmem;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.memory.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryMemoryStoreTest {

    static final String TENANT       = "tenant-1";
    static final String OTHER_TENANT = "tenant-2";
    static final MemoryDomain DOMAIN       = new MemoryDomain("d");
    static final MemoryDomain OTHER_DOMAIN = new MemoryDomain("other");

    private final CurrentPrincipal principal = new CurrentPrincipal() {
        @Override public String actorId()           { return "actor"; }
        @Override public Set<String> groups()       { return Set.of(); }
        @Override public String tenancyId()         { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return false; }
    };

    private InMemoryMemoryStore sut;

    @BeforeEach
    void setUp() {
        sut = new InMemoryMemoryStore(principal);
    }

    private MemoryInput input(String text) {
        return new MemoryInput("entity-1", DOMAIN, TENANT, null, text, Map.of());
    }

    private MemoryInput input(String entityId, String text) {
        return new MemoryInput(entityId, DOMAIN, TENANT, null, text, Map.of());
    }

    private MemoryInput inputWithCase(String text, String caseId) {
        return new MemoryInput("entity-1", DOMAIN, TENANT, caseId, text, Map.of());
    }

    private MemoryQuery query() {
        return MemoryQuery.forEntity("entity-1", DOMAIN, TENANT);
    }

    private EraseRequest eraseRequest() {
        return new EraseRequest("entity-1", DOMAIN, TENANT, null);
    }

    // --- store ---

    @Test
    void store_assigns_non_empty_memory_id() {
        assertFalse(sut.store(input("hello")).isEmpty());
    }

    @Test
    void store_assigns_unique_ids() {
        assertNotEquals(sut.store(input("a")), sut.store(input("b")));
    }

    @Test
    void store_tenant_mismatch_throws() {
        var bad = new MemoryInput("entity-1", DOMAIN, OTHER_TENANT, null, "x", Map.of());
        assertThrows(SecurityException.class, () -> sut.store(bad));
    }

    // --- query single entity ---

    @Test
    void query_returns_stored_memories_in_desc_order() {
        sut.store(input("first"));
        sut.store(input("second"));
        var results = sut.query(query());
        assertEquals(2, results.size());
        assertEquals("second", results.get(0).text());
        assertEquals("first",  results.get(1).text());
    }

    @Test
    void query_empty_when_nothing_stored() {
        assertTrue(sut.query(query()).isEmpty());
    }

    @Test
    void query_does_not_leak_across_tenants() {
        sut.store(input("secret"));
        var crossQuery = MemoryQuery.forEntity("entity-1", DOMAIN, OTHER_TENANT);
        assertThrows(SecurityException.class, () -> sut.query(crossQuery));
    }

    @Test
    void query_does_not_leak_across_domains() {
        sut.store(input("finance fact"));
        var otherDomainQuery = MemoryQuery.forEntity("entity-1", OTHER_DOMAIN, TENANT);
        assertTrue(sut.query(otherDomainQuery).isEmpty());
    }

    @Test
    void query_with_caseId_filters_correctly() {
        sut.store(input("no case"));
        sut.store(inputWithCase("case A", "case-1"));
        sut.store(inputWithCase("case B", "case-2"));

        var results = sut.query(query().withCaseId("case-1"));
        assertEquals(1, results.size());
        assertEquals("case A", results.get(0).text());
    }

    @Test
    void query_null_caseId_returns_all() {
        sut.store(input("no case"));
        sut.store(inputWithCase("with case", "case-1"));
        assertEquals(2, sut.query(query()).size());
    }

    @Test
    void query_with_since_excludes_older_memories() throws InterruptedException {
        sut.store(input("old"));
        Instant barrier = Instant.now();
        Thread.sleep(5);
        sut.store(input("new"));

        var results = sut.query(query().withSince(barrier));
        assertEquals(1, results.size());
        assertEquals("new", results.get(0).text());
    }

    @Test
    void query_limit_is_honoured() {
        for (int i = 0; i < 5; i++) sut.store(input("item " + i));
        assertEquals(3, sut.query(query().withLimit(3)).size());
    }

    @Test
    void query_with_question_filters_by_text_containment() {
        sut.store(input("the cat sat on the mat"));
        sut.store(input("the dog barked loudly"));

        var results = sut.query(query().withQuestion("cat"));
        assertEquals(1, results.size());
        assertEquals("the cat sat on the mat", results.get(0).text());
    }

    @Test
    void query_null_question_returns_all() {
        sut.store(input("anything"));
        sut.store(input("something else"));
        assertEquals(2, sut.query(query()).size());
    }

    // --- multi-entity query ---

    @Test
    void multi_entity_query_returns_facts_from_all_entities() {
        sut.store(input("entity-1", "fact about e1"));
        sut.store(input("entity-2", "fact about e2"));

        var results = sut.query(
            MemoryQuery.forEntities(List.of("entity-1", "entity-2"), DOMAIN, TENANT));
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(m -> "fact about e1".equals(m.text())));
        assertTrue(results.stream().anyMatch(m -> "fact about e2".equals(m.text())));
    }

    @Test
    void multi_entity_query_limit_applies_to_combined_result_set() {
        for (int i = 0; i < 5; i++) sut.store(input("entity-1", "e1-" + i));
        for (int i = 0; i < 5; i++) sut.store(input("entity-2", "e2-" + i));

        var results = sut.query(
            MemoryQuery.forEntities(List.of("entity-1", "entity-2"), DOMAIN, TENANT)
                .withLimit(6));
        assertEquals(6, results.size());
    }

    @Test
    void multi_entity_result_carries_entityId_per_memory() {
        sut.store(input("entity-1", "fact about e1"));
        sut.store(input("entity-2", "fact about e2"));

        var results = sut.query(
            MemoryQuery.forEntities(List.of("entity-1", "entity-2"), DOMAIN, TENANT));
        assertTrue(results.stream().anyMatch(m -> "entity-1".equals(m.entityId())));
        assertTrue(results.stream().anyMatch(m -> "entity-2".equals(m.entityId())));
    }

    // --- MemoryOrder ---

    @Test
    void relevance_order_accepted_without_error() {
        sut.store(input("some text"));
        assertDoesNotThrow(() ->
            sut.query(query().withOrder(MemoryOrder.RELEVANCE).withQuestion("some")));
    }

    // --- MemoryAttributeKeys round-trip ---

    @Test
    void attribute_keys_round_trip_correctly() {
        var attrs = Map.of(
            MemoryAttributeKeys.ACTOR_ID, "actor-123",
            MemoryAttributeKeys.OUTCOME,  "DONE",
            MemoryAttributeKeys.CONFIDENCE, MemoryAttributeKeys.formatConfidence(0.87)
        );
        sut.store(new MemoryInput("entity-1", DOMAIN, TENANT, null, "reviewer completed task", attrs));

        var results = sut.query(query());
        assertEquals(1, results.size());
        var stored = results.get(0).attributes();
        assertEquals("actor-123", stored.get(MemoryAttributeKeys.ACTOR_ID));
        assertEquals("DONE", stored.get(MemoryAttributeKeys.OUTCOME));
        assertEquals(0.87, MemoryAttributeKeys.parseConfidence(stored.get(MemoryAttributeKeys.CONFIDENCE)), 0.0001);
    }

    // --- erase ---

    @Test
    void erase_removes_domain_scoped_memories() {
        sut.store(input("to erase"));
        sut.erase(eraseRequest());
        assertTrue(sut.query(query()).isEmpty());
    }

    @Test
    void erase_with_caseId_leaves_other_cases() {
        sut.store(input("no case"));
        sut.store(inputWithCase("case A", "case-1"));
        sut.store(inputWithCase("case B", "case-2"));

        sut.erase(new EraseRequest("entity-1", DOMAIN, TENANT, "case-1"));

        var remaining = sut.query(query());
        assertEquals(2, remaining.size());
        assertTrue(remaining.stream().anyMatch(m -> "no case".equals(m.text())));
        assertTrue(remaining.stream().anyMatch(m -> "case B".equals(m.text())));
    }

    @Test
    void erase_tenant_mismatch_throws() {
        assertThrows(SecurityException.class,
            () -> sut.erase(new EraseRequest("entity-1", DOMAIN, OTHER_TENANT, null)));
    }

    // --- eraseById ---

    @Test
    void eraseById_removes_specific_memory() {
        String id = sut.store(input("to delete"));
        sut.store(input("to keep"));
        sut.eraseById(id, TENANT);
        var remaining = sut.query(query());
        assertEquals(1, remaining.size());
        assertEquals("to keep", remaining.get(0).text());
    }

    @Test
    void eraseById_does_not_cross_tenant_boundary() {
        sut.store(input("protected"));
        assertThrows(SecurityException.class, () -> sut.eraseById("any-id", OTHER_TENANT));
        assertEquals(1, sut.query(query()).size());
    }

    // --- eraseEntity ---

    @Test
    void eraseEntity_removes_all_domains_for_entity() {
        sut.store(new MemoryInput("entity-1", DOMAIN,       TENANT, null, "finance", Map.of()));
        sut.store(new MemoryInput("entity-1", OTHER_DOMAIN, TENANT, null, "health",  Map.of()));

        sut.eraseEntity("entity-1", TENANT);

        assertTrue(sut.query(query()).isEmpty());
        assertTrue(sut.query(MemoryQuery.forEntity("entity-1", OTHER_DOMAIN, TENANT)).isEmpty());
    }

    @Test
    void eraseEntity_does_not_cross_tenant_boundary() {
        assertThrows(SecurityException.class, () -> sut.eraseEntity("entity-1", OTHER_TENANT));
    }

    @Test
    void eraseEntity_leaves_other_entities_intact() {
        sut.store(new MemoryInput("entity-1", DOMAIN, TENANT, null, "mine",  Map.of()));
        sut.store(new MemoryInput("entity-2", DOMAIN, TENANT, null, "other", Map.of()));

        sut.eraseEntity("entity-1", TENANT);

        var e2results = sut.query(MemoryQuery.forEntity("entity-2", DOMAIN, TENANT));
        assertEquals(1, e2results.size());
        assertEquals("other", e2results.get(0).text());
    }
}
