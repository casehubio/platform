package io.casehub.platform.authn;

import io.casehub.platform.api.authn.OAuthTokenStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpOAuthTokenStoreTest {

    private final OAuthTokenStore store = new NoOpOAuthTokenStore();

    @Test
    void findByActorIdReturnsEmpty() {
        assertThat(store.findByActorId("actor-1", "google", "tenant-1")).isEmpty();
    }

    @Test
    void findAllByActorIdReturnsEmptyList() {
        assertThat(store.findAllByActorId("actor-1", "tenant-1")).isEmpty();
    }

    @Test
    void deleteIsNoOp() {
        store.delete("actor-1", "google", "tenant-1");
    }

    @Test
    void updateTokensIsNoOp() {
        store.updateTokens("actor-1", "google", "tenant-1", "access", "refresh", Instant.now().plusSeconds(3600));
    }
}
