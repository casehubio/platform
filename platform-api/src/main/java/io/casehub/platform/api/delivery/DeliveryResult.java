package io.casehub.platform.api.delivery;

/**
 * Result of a notification delivery attempt.
 *
 * <p>Provides the architectural seam for future transactional outbox
 * and delivery tracking (#154).
 *
 * @param success       whether delivery succeeded
 * @param failureReason optional reason when delivery failed
 */
public record DeliveryResult(
        boolean success,
        String failureReason
) {}
