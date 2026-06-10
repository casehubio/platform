package io.casehub.platform.api.memory;

import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CaseMemoryStoreSpiTest {

    // Anonymous impl omitting all default methods.
    // Compiler error on any omitted method = it is abstract (RED state).
    // Compiles without implementing defaults = they are default (GREEN proves contract).
    final CaseMemoryStore sut = new CaseMemoryStore() {
        @Override public String store(MemoryInput i) { return "mem-1"; }
        @Override public List<Memory> query(MemoryQuery q) { return List.of(); }
        @Override public void erase(EraseRequest r) {}
    };

    static final MemoryDomain DOMAIN = new MemoryDomain("d");

    @Test
    void storeAll_delegates_to_store() {
        var a = new MemoryInput("e1", DOMAIN, "t1", null, "a", Map.of());
        var b = new MemoryInput("e1", DOMAIN, "t1", null, "b", Map.of());
        assertEquals(List.of("mem-1", "mem-1"), sut.storeAll(List.of(a, b)));
    }

    @Test
    void eraseById_default_throws_MemoryCapabilityException() {
        final var ex = assertThrows(MemoryCapabilityException.class,
            () -> sut.eraseById("mem-1", "entity-1", "tenant-1"));
        assertEquals(MemoryCapability.ERASE_BY_ID, ex.required());
    }

    @Test
    void eraseEntity_default_throws_MemoryCapabilityException() {
        final var ex = assertThrows(MemoryCapabilityException.class,
            () -> sut.eraseEntity("entity-1", "tenant-1"));
        assertEquals(MemoryCapability.ERASE_ENTITY, ex.required());
    }

    @Test
    void capabilities_default_returns_empty_set() {
        assertTrue(sut.capabilities().isEmpty());
    }

    @Test
    void requireCapability_throws_for_undeclared_capability() {
        final var ex = assertThrows(MemoryCapabilityException.class,
            () -> sut.requireCapability(MemoryCapability.TEMPORAL_GRAPH));
        assertEquals(MemoryCapability.TEMPORAL_GRAPH, ex.required());
    }

    // Via static utility directly (callable by all adapters)
    @Test
    void memoryPermissions_throws_on_mismatch() {
        assertThrows(SecurityException.class,
            () -> MemoryPermissions.assertTenant("wrong", principal("real")));
    }

    @Test
    void memoryPermissions_passes_on_match() {
        assertDoesNotThrow(() -> MemoryPermissions.assertTenant("real", principal("real")));
    }

    private static CurrentPrincipal principal(String tenancyId) {
        return new CurrentPrincipal() {
            @Override public String actorId() { return "actor"; }
            @Override public Set<String> groups() { return Set.of(); }
            @Override public String tenancyId() { return tenancyId; }
            @Override public boolean isCrossTenantAdmin() { return false; }
        };
    }
}
