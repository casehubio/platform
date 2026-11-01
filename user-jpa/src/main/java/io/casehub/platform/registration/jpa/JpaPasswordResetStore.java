package io.casehub.platform.registration.jpa;

import io.casehub.platform.registration.PasswordResetStore;
import io.casehub.platform.registration.PasswordResetToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@ApplicationScoped
public class JpaPasswordResetStore implements PasswordResetStore {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void store(PasswordResetToken token) {
        em.persist(PasswordResetTokenEntity.fromToken(token));
    }

    @Override
    @Transactional
    public Optional<PasswordResetToken> consume(String rawToken) {
        String hash = sha256Hex(rawToken);
        PasswordResetTokenEntity entity = em.find(PasswordResetTokenEntity.class, hash);
        if (entity == null) {
            return Optional.empty();
        }
        if (entity.expiresAt.isBefore(Instant.now())) {
            em.remove(entity);
            return Optional.empty();
        }
        PasswordResetToken token = entity.toToken();
        em.remove(entity);
        return Optional.of(token);
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
