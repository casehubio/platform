package io.casehub.platform.api.delivery;

import java.time.Instant;
import java.util.Objects;

public record DeliveryAttempt(
        String id,
        String notificationId,
        String channelId,
        String userId,
        String tenancyId,
        DeliveryType deliveryType,
        DeliveryStatus status,
        int attemptCount,
        Instant createdAt,
        Instant lastAttemptedAt,
        Instant deliveredAt,
        Instant nextRetryAt,
        String failureReason,
        String payload
) {
    public DeliveryAttempt {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(deliveryType, "deliveryType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(payload, "payload");
    }
}
