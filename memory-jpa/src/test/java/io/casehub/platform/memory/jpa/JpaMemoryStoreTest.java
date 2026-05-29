package io.casehub.platform.memory.jpa;

import io.casehub.platform.api.memory.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JpaMemoryStoreTest {

    static final String TENANT = "tenant-1";
    static final MemoryDomain DOMAIN = new MemoryDomain("d");

    @Inject JpaMemoryStore store;

    @Test
    @Transactional
    void store_assigns_non_empty_memory_id() {
        String id = store.store(new MemoryInput("entity-1", DOMAIN, TENANT, null, "hello", Map.of()));
        assertFalse(id.isEmpty());
    }
}
