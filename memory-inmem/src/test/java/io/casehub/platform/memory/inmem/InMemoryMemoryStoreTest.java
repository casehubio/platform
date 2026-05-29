package io.casehub.platform.memory.inmem;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.memory.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryMemoryStoreTest {

    static final String TENANT = "tenant-1";
    static final MemoryDomain DOMAIN = new MemoryDomain("d");

    private final CurrentPrincipal principal = new CurrentPrincipal() {
        @Override public String actorId() { return "actor"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return false; }
    };

    private InMemoryMemoryStore sut = new InMemoryMemoryStore(principal);

    @Test
    void store_assigns_non_empty_memory_id() {
        String id = sut.store(new MemoryInput("entity-1", DOMAIN, TENANT, null, "hello", Map.of()));
        assertFalse(id.isEmpty());
    }
}
