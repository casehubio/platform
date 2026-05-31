package io.casehub.platform.memory.inmem;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.memory.*;
import io.casehub.platform.testing.memory.CaseMemoryStoreContractTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryMemoryStoreTest extends CaseMemoryStoreContractTest {

    private final CurrentPrincipal principal = new CurrentPrincipal() {
        @Override public String actorId()             { return "actor"; }
        @Override public Set<String> groups()         { return Set.of(); }
        @Override public String tenancyId()           { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return false; }
    };

    private InMemoryMemoryStore sut;

    @BeforeEach
    void setUp() {
        sut = new InMemoryMemoryStore(principal);
    }

    @Override
    protected CaseMemoryStore store() {
        return sut;
    }

    // In-mem specific: question + CHRONOLOGICAL does substring filter (adapter behaviour, not contract)
    @Test
    void query_with_question_filters_by_text_containment() {
        sut.store(input("the cat sat on the mat"));
        sut.store(input("the dog barked loudly"));
        var results = sut.query(query().withQuestion("cat"));
        assertEquals(1, results.size());
        assertEquals("the cat sat on the mat", results.get(0).text());
    }

    @Test
    void relevance_order_accepted_without_error() {
        sut.store(input("some text"));
        assertDoesNotThrow(() ->
            sut.query(query().withOrder(MemoryOrder.RELEVANCE).withQuestion("some")));
    }
}
