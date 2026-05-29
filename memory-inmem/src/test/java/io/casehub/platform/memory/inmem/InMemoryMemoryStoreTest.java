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
        @Override public String actorId() { return "actor"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return TENANT; }
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

    private MemoryInput inputWithCase(String text, String caseId) {
        return new MemoryInput("entity-1", DOMAIN, TENANT, caseId, text, Map.of());
    }

    private MemoryQuery query() {
        return new MemoryQuery("entity-1", DOMAIN, TENANT, null, null, 10, null);
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

    // --- query ---

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
        var crossQuery = new MemoryQuery("entity-1", DOMAIN, OTHER_TENANT, null, null, 10, null);
        assertThrows(SecurityException.class, () -> sut.query(crossQuery));
    }

    @Test
    void query_does_not_leak_across_domains() {
        sut.store(input("finance fact"));
        var otherDomainQuery = new MemoryQuery("entity-1", OTHER_DOMAIN, TENANT, null, null, 10, null);
        assertTrue(sut.query(otherDomainQuery).isEmpty());
    }

    @Test
    void query_with_caseId_filters_correctly() {
        sut.store(input("no case"));
        sut.store(inputWithCase("case A", "case-1"));
        sut.store(inputWithCase("case B", "case-2"));

        var caseQuery = new MemoryQuery("entity-1", DOMAIN, TENANT, "case-1", null, 10, null);
        var results = sut.query(caseQuery);
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

        var sinceQuery = new MemoryQuery("entity-1", DOMAIN, TENANT, null, null, 10, barrier);
        var results = sut.query(sinceQuery);
        assertEquals(1, results.size());
        assertEquals("new", results.get(0).text());
    }

    @Test
    void query_limit_is_honoured() {
        for (int i = 0; i < 5; i++) sut.store(input("item " + i));
        var limitQuery = new MemoryQuery("entity-1", DOMAIN, TENANT, null, null, 3, null);
        assertEquals(3, sut.query(limitQuery).size());
    }

    @Test
    void query_with_question_filters_by_text_containment() {
        sut.store(input("the cat sat on the mat"));
        sut.store(input("the dog barked loudly"));

        var questionQuery = new MemoryQuery("entity-1", DOMAIN, TENANT, null, "cat", 10, null);
        var results = sut.query(questionQuery);
        assertEquals(1, results.size());
        assertEquals("the cat sat on the mat", results.get(0).text());
    }

    @Test
    void query_null_question_returns_all() {
        sut.store(input("anything"));
        sut.store(input("something else"));
        assertEquals(2, sut.query(query()).size());
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
        assertTrue(sut.query(new MemoryQuery("entity-1", OTHER_DOMAIN, TENANT, null, null, 10, null)).isEmpty());
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

        var e2query = new MemoryQuery("entity-2", DOMAIN, TENANT, null, null, 10, null);
        assertEquals(1, sut.query(e2query).size());
        assertEquals("other", sut.query(e2query).get(0).text());
    }
}
