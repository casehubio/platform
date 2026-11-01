package io.casehub.platform.registration.spring.jpa;

import io.casehub.platform.registration.PasswordResetStore;
import io.casehub.platform.registration.PasswordResetToken;
import io.casehub.platform.registration.jpa.PasswordResetTokenEntity;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

public class SpringPasswordResetStore implements PasswordResetStore {

    private final PasswordResetTokenEntityRepository repo;

    public SpringPasswordResetStore(PasswordResetTokenEntityRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void store(PasswordResetToken token) {
        repo.save(PasswordResetTokenEntity.fromToken(token));
    }

    @Override
    @Transactional
    public Optional<PasswordResetToken> consume(String rawToken) {
        String hash = sha256Hex(rawToken);
        return repo.findById(hash).map(entity -> {
            if (entity.expiresAt.isBefore(Instant.now())) {
                repo.delete(entity);
                return null;
            }
            PasswordResetToken token = entity.toToken();
            repo.delete(entity);
            return token;
        });
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
