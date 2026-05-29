package io.casehub.platform.memory;

import io.casehub.platform.api.memory.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class NoOpCaseMemoryStoreTest {

    @Inject CaseMemoryStore store;
    @Inject ReactiveCaseMemoryStore reactiveStore;

    static final MemoryDomain DOMAIN  = new MemoryDomain("test");
    static final MemoryInput  SAMPLE  = new MemoryInput(
        "entity-1", DOMAIN, "tenant-1", null, "sample", Map.of());
    static final MemoryInput  SAMPLE_WITH_CASE = new MemoryInput(
        "entity-1", DOMAIN, "tenant-1", "case-99", "sample", Map.of());
    static final MemoryQuery  QUERY   = new MemoryQuery(
        "entity-1", DOMAIN, "tenant-1", null, null, 10, null);
    static final EraseRequest ERASE_SCOPED = new EraseRequest(
        "entity-1", DOMAIN, "tenant-1", null);

    // --- blocking no-op ---
    @Test void store_returns_empty_id()      { assertTrue(store.store(SAMPLE).isEmpty()); }
    @Test void query_returns_empty()         { assertTrue(store.query(QUERY).isEmpty()); }
    @Test void erase_scoped_does_not_throw() { assertDoesNotThrow(() -> store.erase(ERASE_SCOPED)); }
    @Test void eraseById_does_not_throw()    { assertDoesNotThrow(() -> store.eraseById("mem-1", "tenant-1")); }
    @Test void eraseEntity_does_not_throw()  { assertDoesNotThrow(() -> store.eraseEntity("entity-1", "tenant-1")); }
    @Test void storeAll_returns_empty_ids() {
        assertEquals(List.of("", ""), store.storeAll(List.of(SAMPLE, SAMPLE_WITH_CASE)));
    }

    // --- reactive bridge (delegates to blocking no-op) ---
    @Test void bridge_store_returns_empty_id() {
        assertTrue(reactiveStore.store(SAMPLE).await().indefinitely().isEmpty());
    }
    @Test void bridge_query_returns_empty() {
        assertTrue(reactiveStore.query(QUERY).await().indefinitely().isEmpty());
    }
    @Test void bridge_erase_does_not_throw() {
        assertDoesNotThrow(() -> reactiveStore.erase(ERASE_SCOPED).await().indefinitely());
    }
    @Test void bridge_eraseById_does_not_throw() {
        assertDoesNotThrow(() -> reactiveStore.eraseById("mem-1", "tenant-1").await().indefinitely());
    }
    @Test void bridge_eraseEntity_does_not_throw() {
        assertDoesNotThrow(() -> reactiveStore.eraseEntity("entity-1", "tenant-1").await().indefinitely());
    }
}
