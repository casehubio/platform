package io.casehub.platform.api.delivery;

import java.time.Instant;
import java.util.List;

public interface DeliveryAttemptStore {
    void store(DeliveryAttempt attempt);
    void update(DeliveryAttempt attempt);
    List<DeliveryAttempt> claimRetryable(Instant now, int batchSize);
    DeliveryAttemptPage find(DeliveryAttemptQuery query);
    List<DeliveryAttempt> findByNotificationId(String notificationId);
}
