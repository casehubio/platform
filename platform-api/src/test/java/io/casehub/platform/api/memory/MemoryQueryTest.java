package io.casehub.platform.api.memory;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class MemoryQueryTest {

    static final MemoryDomain DOMAIN = new MemoryDomain("test");

    @Test
    void valid_query_constructs() {
        var q = new MemoryQuery("e1", DOMAIN, "t1", null, null, 10, null);
        assertEquals("e1", q.entityId());
        assertEquals(10, q.limit());
        assertNull(q.question());
        assertNull(q.since());
    }

    @Test
    void null_entityId_throws() {
        assertThrows(NullPointerException.class,
            () -> new MemoryQuery(null, DOMAIN, "t1", null, null, 10, null));
    }

    @Test
    void null_domain_throws() {
        assertThrows(NullPointerException.class,
            () -> new MemoryQuery("e1", null, "t1", null, null, 10, null));
    }

    @Test
    void null_tenantId_throws() {
        assertThrows(NullPointerException.class,
            () -> new MemoryQuery("e1", DOMAIN, null, null, null, 10, null));
    }

    @Test
    void zero_limit_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new MemoryQuery("e1", DOMAIN, "t1", null, null, 0, null));
    }

    @Test
    void negative_limit_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new MemoryQuery("e1", DOMAIN, "t1", null, null, -1, null));
    }

    @Test
    void limit_of_one_is_valid() {
        assertDoesNotThrow(() -> new MemoryQuery("e1", DOMAIN, "t1", null, null, 1, null));
    }

    @Test
    void caseId_question_since_accept_non_null_values() {
        assertDoesNotThrow(() ->
            new MemoryQuery("e1", DOMAIN, "t1", "case-1", "what do we know?", 5, Instant.now()));
    }
}
