package io.casehub.platform.registration.jpa;

import io.casehub.platform.registration.EmailVerificationStore;
import io.casehub.platform.registration.EmailVerificationToken;
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
public class JpaEmailVerificationStore implements EmailVerificationStore {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void store(EmailVerificationToken token) {
        em.persist(EmailVerificationEntity.fromToken(token));
    }

    @Override
    @Transactional
    public Optional<EmailVerificationToken> consume(String rawToken) {
        String hash = sha256Hex(rawToken);
        EmailVerificationEntity entity = em.find(EmailVerificationEntity.class, hash);
        if (entity == null) {
            return Optional.empty();
        }
        if (entity.expiresAt.isBefore(Instant.now())) {
            em.remove(entity);
            return Optional.empty();
        }
        EmailVerificationToken token = entity.toToken();
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
