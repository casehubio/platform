package io.casehub.platform.registration.spring.jpa;

import io.casehub.platform.registration.EmailVerificationStore;
import io.casehub.platform.registration.EmailVerificationToken;
import io.casehub.platform.registration.jpa.EmailVerificationEntity;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

public class SpringEmailVerificationStore implements EmailVerificationStore {

    private final EmailVerificationEntityRepository repo;

    public SpringEmailVerificationStore(EmailVerificationEntityRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void store(EmailVerificationToken token) {
        repo.save(EmailVerificationEntity.fromToken(token));
    }

    @Override
    @Transactional
    public Optional<EmailVerificationToken> consume(String rawToken) {
        String hash = sha256Hex(rawToken);
        return repo.findById(hash).map(entity -> {
            if (entity.expiresAt.isBefore(Instant.now())) {
                repo.delete(entity);
                return null;
            }
            EmailVerificationToken token = entity.toToken();
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
