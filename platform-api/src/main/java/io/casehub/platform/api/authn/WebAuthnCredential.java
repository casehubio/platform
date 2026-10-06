package io.casehub.platform.api.authn;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

public record WebAuthnCredential(
    String credentialId,
    String actorId,
    String tenancyId,
    byte[] publicKeyCose,
    long signCount,
    Set<String> transports,
    String aaguid,
    String displayName,
    Instant createdAt,
    Instant lastUsedAt,
    boolean discoverable
) {
    public WebAuthnCredential {
        Objects.requireNonNull(credentialId, "credentialId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        publicKeyCose = publicKeyCose != null ? Arrays.copyOf(publicKeyCose, publicKeyCose.length) : null;
        transports = transports != null ? Set.copyOf(transports) : Set.of();
    }

    @Override
    public byte[] publicKeyCose() {
        return publicKeyCose != null ? Arrays.copyOf(publicKeyCose, publicKeyCose.length) : null;
    }
}
