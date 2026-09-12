package io.casehub.platform.credentials;

import io.casehub.platform.api.credentials.LlmCredentialStore;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class NoOpLlmCredentialStoreTest {

    private final LlmCredentialStore store = new NoOpLlmCredentialStore();

    @Test
    void resolveReturnsEmptyMap() {
        assertThat(store.resolve("tenant-1", "ref-1")).isEmpty();
    }

    @Test
    void storeIsNoOp() {
        store.store("tenant-1", "ref-1", Map.of("api-key", "secret"));
        assertThat(store.resolve("tenant-1", "ref-1")).isEmpty();
    }

    @Test
    void deleteIsNoOp() {
        store.delete("tenant-1", "ref-1");
    }

    @Test
    void listRefsReturnsEmptyList() {
        assertThat(store.listRefs("tenant-1")).isEmpty();
    }
}
