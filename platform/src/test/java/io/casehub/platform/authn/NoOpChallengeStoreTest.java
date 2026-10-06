package io.casehub.platform.authn;

import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.authn.ChallengeStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpChallengeStoreTest {

    private final ChallengeStore store = new NoOpChallengeStore();

    @Test
    void storeIsNoOp() {
        var record = new ChallengeRecord("challenge-1", "webauthn", "tenant-1", null, Instant.now(), Instant.now().plusSeconds(300));
        store.store(record);
        assertThat(store.consume("challenge-1")).isEmpty();
    }

    @Test
    void consumeReturnsEmpty() {
        assertThat(store.consume("nonexistent")).isEmpty();
    }
}
