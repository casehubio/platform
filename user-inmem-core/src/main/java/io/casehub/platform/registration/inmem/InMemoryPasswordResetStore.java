package io.casehub.platform.registration.inmem;

import io.casehub.platform.registration.PasswordResetStore;
import io.casehub.platform.registration.PasswordResetToken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPasswordResetStore implements PasswordResetStore {

    private final ConcurrentHashMap<String, PasswordResetToken> store = new ConcurrentHashMap<>();

    @Override
    public void store(PasswordResetToken token) {
        store.put(token.tokenHash(), token);
    }

    @Override
    public Optional<PasswordResetToken> consume(String rawToken) {
        String hash = sha256Hex(rawToken);
        var token = store.remove(hash);
        if (token == null) {
            return Optional.empty();
        }
        if (token.expiresAt() != null && token.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(token);
    }

    static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
