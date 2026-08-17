package io.casehub.platform.api.callback;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record CallbackRegistration(
        String id,
        String spiName,
        String callbackUrl,
        String credentialRef,
        String tenancyId,
        int timeoutMs,
        Map<String, String> metadata,
        Instant registeredAt,
        Instant expiresAt,
        Instant lastHeartbeatAt) {

    public CallbackRegistration {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(spiName, "spiName");
        Objects.requireNonNull(callbackUrl, "callbackUrl");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(registeredAt, "registeredAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(lastHeartbeatAt, "lastHeartbeatAt");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
