package io.casehub.platform.registration.inmem;

import io.casehub.platform.registration.PasswordResetToken;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryPasswordResetStoreTest {

    private final InMemoryPasswordResetStore store = new InMemoryPasswordResetStore();

    @Test
    void storeAndConsume_returnsToken() {
        String rawToken = "test-raw-token";
        String hash = sha256Hex(rawToken);
        var token = new PasswordResetToken(hash, "user-1", "tenant-1",
                Instant.now().plusSeconds(3600), Instant.now());
        store.store(token);

        var result = store.consume(rawToken);
        assertTrue(result.isPresent());
        assertEquals("user-1", result.get().userId());
    }

    @Test
    void consume_removesToken_singleUse() {
        String rawToken = "test-raw-token";
        String hash = sha256Hex(rawToken);
        var token = new PasswordResetToken(hash, "user-1", "tenant-1",
                Instant.now().plusSeconds(3600), Instant.now());
        store.store(token);

        store.consume(rawToken);
        var second = store.consume(rawToken);
        assertTrue(second.isEmpty());
    }

    @Test
    void consume_expired_returnsEmpty() {
        String rawToken = "test-raw-token";
        String hash = sha256Hex(rawToken);
        var token = new PasswordResetToken(hash, "user-1", "tenant-1",
                Instant.now().minusSeconds(1), Instant.now().minusSeconds(3600));
        store.store(token);

        var result = store.consume(rawToken);
        assertTrue(result.isEmpty());
    }

    @Test
    void consume_unknownToken_returnsEmpty() {
        var result = store.consume("nonexistent");
        assertTrue(result.isEmpty());
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
