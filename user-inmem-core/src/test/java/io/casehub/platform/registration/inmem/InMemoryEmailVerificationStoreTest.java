package io.casehub.platform.registration.inmem;

import io.casehub.platform.registration.EmailVerificationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEmailVerificationStoreTest {

    private InMemoryEmailVerificationStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryEmailVerificationStore();
    }

    @Test
    void storeAndConsume() {
        String rawToken = "abc123-raw-token";
        String hash = InMemoryEmailVerificationStore.sha256Hex(rawToken);
        var token = new EmailVerificationToken(hash, "u1", "t1",
                Instant.now().plusSeconds(3600), Instant.now());
        store.store(token);

        var consumed = store.consume(rawToken);
        assertThat(consumed).isPresent();
        assertThat(consumed.get().userId()).isEqualTo("u1");
    }

    @Test
    void consumeIsSingleUse() {
        String rawToken = "single-use-token";
        String hash = InMemoryEmailVerificationStore.sha256Hex(rawToken);
        store.store(new EmailVerificationToken(hash, "u1", "t1",
                Instant.now().plusSeconds(3600), Instant.now()));

        assertThat(store.consume(rawToken)).isPresent();
        assertThat(store.consume(rawToken)).isEmpty();
    }

    @Test
    void consumeExpiredTokenReturnsEmpty() {
        String rawToken = "expired-token";
        String hash = InMemoryEmailVerificationStore.sha256Hex(rawToken);
        store.store(new EmailVerificationToken(hash, "u1", "t1",
                Instant.now().minusSeconds(3600), Instant.now().minusSeconds(7200)));

        assertThat(store.consume(rawToken)).isEmpty();
    }

    @Test
    void consumeNonExistentTokenReturnsEmpty() {
        assertThat(store.consume("no-such-token")).isEmpty();
    }
}
