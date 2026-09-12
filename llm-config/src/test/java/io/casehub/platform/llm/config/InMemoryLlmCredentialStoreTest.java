package io.casehub.platform.llm.config;

import io.casehub.platform.api.credentials.LlmCredentialStore;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLlmCredentialStoreTest {

    private final LlmCredentialStore store = new InMemoryLlmCredentialStore();

    @Test
    void storeAndResolve() {
        store.store("t1", "ref-1", Map.of("api-key", "sk-123"));
        assertThat(store.resolve("t1", "ref-1")).containsEntry("api-key", "sk-123");
    }

    @Test
    void resolveUnknownReturnsEmpty() {
        assertThat(store.resolve("t1", "unknown")).isEmpty();
    }

    @Test
    void tenantIsolation() {
        store.store("t1", "ref-1", Map.of("api-key", "t1-key"));
        store.store("t2", "ref-1", Map.of("api-key", "t2-key"));
        assertThat(store.resolve("t1", "ref-1")).containsEntry("api-key", "t1-key");
        assertThat(store.resolve("t2", "ref-1")).containsEntry("api-key", "t2-key");
    }

    @Test
    void deleteRemovesCredentials() {
        store.store("t1", "ref-1", Map.of("api-key", "sk-123"));
        store.delete("t1", "ref-1");
        assertThat(store.resolve("t1", "ref-1")).isEmpty();
    }

    @Test
    void listRefsReturnsStoredRefs() {
        store.store("t1", "ref-a", Map.of("api-key", "a"));
        store.store("t1", "ref-b", Map.of("api-key", "b"));
        store.store("t2", "ref-c", Map.of("api-key", "c"));
        assertThat(store.listRefs("t1")).containsExactlyInAnyOrder("ref-a", "ref-b");
    }

    @Test
    void storeDefensiveCopy() {
        var mutable = new java.util.HashMap<>(Map.of("api-key", "original"));
        store.store("t1", "ref-1", mutable);
        mutable.put("api-key", "mutated");
        assertThat(store.resolve("t1", "ref-1")).containsEntry("api-key", "original");
    }
}
