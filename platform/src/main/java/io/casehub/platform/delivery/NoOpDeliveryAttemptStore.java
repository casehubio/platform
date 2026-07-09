package io.casehub.platform.delivery;

import io.casehub.platform.api.delivery.DeliveryAttempt;
import io.casehub.platform.api.delivery.DeliveryAttemptPage;
import io.casehub.platform.api.delivery.DeliveryAttemptQuery;
import io.casehub.platform.api.delivery.DeliveryAttemptStore;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;

@DefaultBean
@ApplicationScoped
public class NoOpDeliveryAttemptStore implements DeliveryAttemptStore {

    @Override
    public void store(DeliveryAttempt attempt) {}

    @Override
    public void update(DeliveryAttempt attempt) {}

    @Override
    public List<DeliveryAttempt> claimRetryable(Instant now, int batchSize) {
        return List.of();
    }

    @Override
    public DeliveryAttemptPage find(DeliveryAttemptQuery query) {
        return new DeliveryAttemptPage(List.of(), null);
    }

    @Override
    public List<DeliveryAttempt> findByNotificationId(String notificationId) {
        return List.of();
    }
}
