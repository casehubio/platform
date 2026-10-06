package io.casehub.platform.authn;

import io.casehub.platform.api.authn.RefreshTokenStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpRefreshTokenStoreTest {

    private final RefreshTokenStore store = new NoOpRefreshTokenStore();

    @Test
    void findByTokenReturnsEmpty() {
        assertThat(store.findByToken("token-1")).isEmpty();
    }

    @Test
    void consumeIsNoOp() {
        store.consume("token-1");
    }

    @Test
    void revokeFamilyIsNoOp() {
        store.revokeFamily("family-1");
    }

    @Test
    void revokeByActorIdIsNoOp() {
        store.revokeByActorId("actor-1", "tenant-1");
    }

    @Test
    void purgeExpiredReturnsZero() {
        assertThat(store.purgeExpired(Instant.now())).isZero();
    }
}
