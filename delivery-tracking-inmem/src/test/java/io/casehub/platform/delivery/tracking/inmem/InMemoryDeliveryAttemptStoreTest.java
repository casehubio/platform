package io.casehub.platform.delivery.tracking.inmem;

import io.casehub.platform.api.delivery.DeliveryAttempt;
import io.casehub.platform.api.delivery.DeliveryAttemptQuery;
import io.casehub.platform.api.delivery.DeliveryStatus;
import io.casehub.platform.api.delivery.DeliveryType;
import io.casehub.platform.api.notification.UUIDv7;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryDeliveryAttemptStoreTest {

    private InMemoryDeliveryAttemptStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryDeliveryAttemptStore(10000);
    }

    @Test
    void storeAndFindByNotificationId() {
        var attempt = attempt("notif-1", DeliveryStatus.DELIVERED);
        store.store(attempt);

        var found = store.findByNotificationId("notif-1");
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().id()).isEqualTo(attempt.id());
        assertThat(found.getFirst().channelId()).isEqualTo("email");
        assertThat(found.getFirst().deliveryType()).isEqualTo(DeliveryType.IMMEDIATE);
    }

    @Test
    void claimRetryableReturnsOnlyEligible() {
        var now = Instant.now();
        var past = attempt(DeliveryStatus.RETRYING, now.minus(Duration.ofMinutes(1)));
        var future = attempt(DeliveryStatus.RETRYING, now.plus(Duration.ofMinutes(5)));
        store.store(past);
        store.store(future);

        var claimed = store.claimRetryable(now, 10);
        assertThat(claimed).hasSize(1);
        assertThat(claimed.getFirst().id()).isEqualTo(past.id());
    }

    @Test
    void claimRetryableAdvancesNextRetryAt() {
        var now = Instant.now();
        var retryable = attempt(DeliveryStatus.RETRYING, now.minus(Duration.ofMinutes(1)));
        store.store(retryable);

        store.claimRetryable(now, 10);

        var afterClaim = store.findByNotificationId(retryable.notificationId());
        assertThat(afterClaim.getFirst().nextRetryAt()).isAfter(now);
    }

    @Test
    void claimRetryableRespectsMaxBatchSize() {
        var now = Instant.now();
        for (int i = 0; i < 5; i++) {
            store.store(attempt(DeliveryStatus.RETRYING, now.minus(Duration.ofMinutes(1))));
        }

        var claimed = store.claimRetryable(now, 2);
        assertThat(claimed).hasSize(2);
    }

    @Test
    void findByQueryFilters() {
        store.store(attempt("notif-1", "user-a", "tenant-1", "email", DeliveryStatus.DELIVERED));
        store.store(attempt("notif-2", "user-b", "tenant-1", "sms", DeliveryStatus.FAILED));
        store.store(attempt("notif-3", "user-a", "tenant-1", "email", DeliveryStatus.RETRYING));

        var byUser = store.find(new DeliveryAttemptQuery("user-a", "tenant-1", null, null, null, 10));
        assertThat(byUser.attempts()).hasSize(2);

        var byChannel = store.find(new DeliveryAttemptQuery(null, "tenant-1", "sms", null, null, 10));
        assertThat(byChannel.attempts()).hasSize(1);

        var byStatus = store.find(new DeliveryAttemptQuery(null, "tenant-1", null, DeliveryStatus.DELIVERED, null, 10));
        assertThat(byStatus.attempts()).hasSize(1);
    }

    @Test
    void updateModifiesExistingRecord() {
        var attempt = attempt("notif-1", DeliveryStatus.RETRYING);
        store.store(attempt);

        var updated = new DeliveryAttempt(
                attempt.id(), attempt.notificationId(), attempt.channelId(),
                attempt.userId(), attempt.tenancyId(), attempt.deliveryType(),
                DeliveryStatus.DELIVERED, 2,
                attempt.createdAt(), Instant.now(), Instant.now(), null, null, attempt.payload());
        store.update(updated);

        var found = store.findByNotificationId("notif-1");
        assertThat(found.getFirst().status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(found.getFirst().attemptCount()).isEqualTo(2);
    }

    @Test
    void evictsWhenMaxSizeExceeded() {
        store = new InMemoryDeliveryAttemptStore(3);
        for (int i = 0; i < 5; i++) {
            store.store(attempt("notif-" + i, DeliveryStatus.DELIVERED));
        }

        var all = store.find(new DeliveryAttemptQuery(null, "tenant-1", null, null, null, 100));
        assertThat(all.attempts()).hasSize(3);
    }

    @Test
    void findByNotificationIdReturnsEmptyForUnknown() {
        var all = store.findByNotificationId("unknown");
        assertThat(all).isEmpty();
    }

    @Test
    void claimRetryableSkipsNullNextRetryAt() {
        var attempt = new DeliveryAttempt(
                UUIDv7.generate(), "notif-1", "email", "user-1", "tenant-1",
                DeliveryType.DIGEST, DeliveryStatus.RETRYING, 0,
                Instant.now(), null, null, null, null, "{}");
        store.store(attempt);

        var claimed = store.claimRetryable(Instant.now(), 10);
        assertThat(claimed).isEmpty();
    }

    @Test
    void findWithPagination() {
        for (int i = 0; i < 5; i++) {
            store.store(attempt("notif-" + i, DeliveryStatus.DELIVERED));
        }

        var page1 = store.find(new DeliveryAttemptQuery(null, "tenant-1", null, null, null, 2));
        assertThat(page1.attempts()).hasSize(2);
        assertThat(page1.nextCursor()).isNotNull();

        var page2 = store.find(new DeliveryAttemptQuery(null, "tenant-1", null, null, page1.nextCursor(), 2));
        assertThat(page2.attempts()).hasSize(2);

        var page3 = store.find(new DeliveryAttemptQuery(null, "tenant-1", null, null, page2.nextCursor(), 2));
        assertThat(page3.attempts()).hasSize(1);
        assertThat(page3.nextCursor()).isNull();
    }

    // --- helpers ---

    private DeliveryAttempt attempt(String notificationId, DeliveryStatus status) {
        return attempt(notificationId, "user-1", "tenant-1", "email", status);
    }

    private DeliveryAttempt attempt(DeliveryStatus status, Instant nextRetryAt) {
        return new DeliveryAttempt(
                UUIDv7.generate(), "notif-x", "email", "user-1", "tenant-1",
                DeliveryType.IMMEDIATE, status, 1,
                Instant.now(), Instant.now(), null, nextRetryAt, "timeout", "{}");
    }

    private DeliveryAttempt attempt(String notificationId, String userId, String tenancyId,
                                    String channelId, DeliveryStatus status) {
        return new DeliveryAttempt(
                UUIDv7.generate(), notificationId, channelId, userId, tenancyId,
                DeliveryType.IMMEDIATE, status, 1,
                Instant.now(), Instant.now(),
                status == DeliveryStatus.DELIVERED ? Instant.now() : null,
                null, null, "{}");
    }
}
