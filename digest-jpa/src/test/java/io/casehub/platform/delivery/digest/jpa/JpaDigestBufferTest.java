package io.casehub.platform.delivery.digest.jpa;

import io.casehub.platform.api.delivery.DigestBuffer;
import io.casehub.platform.api.delivery.DigestBufferKey;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import io.quarkus.test.TestTransaction;
import jakarta.transaction.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.delivery.DeliveryAttemptStore;
import io.casehub.platform.api.delivery.DigestSummary;
import io.casehub.platform.notification.dispatch.DeliveryTracker;
import java.time.Duration;
import java.time.Instant;
import io.quarkus.narayana.jta.QuarkusTransaction;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class JpaDigestBufferTest {

    @Inject
    DigestBuffer buffer;

    @Inject
    EntityManager entityManager;

    @Inject
    DeliveryAttemptStore attempts;

    @Inject
    ObjectMapper mapper;

    private static final DigestBufferKey KEY = new DigestBufferKey("user-1", "tenant-1", "email");

    @BeforeEach
    @Transactional
    void setUp() {
        entityManager.createQuery("DELETE FROM DigestBufferEntity").executeUpdate();
        entityManager.createQuery("DELETE FROM DeliveryAttemptEntity").executeUpdate();
    }

    @Test
    @TestTransaction
    void add_then_drain_returnsItems() {
        buffer.add(KEY, sampleInput("Title 1"));
        buffer.add(KEY, sampleInput("Title 2"));

        var items = buffer.drain(KEY);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).title()).isEqualTo("Title 1");
        assertThat(items.get(1).title()).isEqualTo("Title 2");
    }

    @Test
    @TestTransaction
    void drain_clearsBuffer() {
        buffer.add(KEY, sampleInput("Title 1"));
        buffer.drain(KEY);

        assertThat(buffer.pendingKeys()).isEmpty();
        assertThat(buffer.drain(KEY)).isEmpty();
    }

    @Test
    @TestTransaction
    void drain_unknownKey_returnsEmpty() {
        assertThat(buffer.drain(KEY)).isEmpty();
    }

    @Test
    @TestTransaction
    void pendingKeys_returnsOnlyKeysWithItems() {
        var key2 = new DigestBufferKey("user-2", "tenant-1", "email");
        buffer.add(KEY, sampleInput("Title 1"));
        buffer.add(key2, sampleInput("Title 2"));

        assertThat(buffer.pendingKeys()).containsExactlyInAnyOrder(KEY, key2);
    }

    @Test
    @TestTransaction
    void oldestPendingTimestamp_returnsFirstAddTime() {
        buffer.add(KEY, sampleInput("Title 1"));
        var firstTimestamp = buffer.oldestPendingTimestamp(KEY);
        assertThat(firstTimestamp).isPresent();

        buffer.add(KEY, sampleInput("Title 2"));
        var secondTimestamp = buffer.oldestPendingTimestamp(KEY);

        assertThat(secondTimestamp).isEqualTo(firstTimestamp);
    }

    @Test
    @TestTransaction
    void oldestPendingTimestamp_unknownKey_returnsEmpty() {
        assertThat(buffer.oldestPendingTimestamp(KEY)).isEmpty();
    }

    @Test
    @TestTransaction
    void pendingCount_returnsItemCount() {
        buffer.add(KEY, sampleInput("one"));
        buffer.add(KEY, sampleInput("two"));
        assertThat(buffer.pendingCount(KEY)).isEqualTo(2);
    }

    @Test
    @TestTransaction
    void pendingCount_returnsZero_whenKeyAbsent() {
        assertThat(buffer.pendingCount(KEY)).isEqualTo(0);
    }

    @Test
    @TestTransaction
    void pendingKeysForUser_filtersToUser() {
        var otherKey = new DigestBufferKey("other-user", "tenant-1", "email");
        buffer.add(KEY, sampleInput("mine"));
        buffer.add(otherKey, sampleInput("theirs"));

        var keys = buffer.pendingKeysForUser("user-1", "tenant-1");
        assertThat(keys).containsExactly(KEY);
    }

    @Test
    @TestTransaction
    void eviction_dropsOldestWhenMaxExceeded() {
        // This test only exercises the eviction path when maxBufferSize > 0.
        // Default is 0 (no eviction). Override via config to enable.
        // For now, test the no-eviction default — eviction tested via direct construction below.
        buffer.add(KEY, sampleInput("Item 1"));
        buffer.add(KEY, sampleInput("Item 2"));
        buffer.add(KEY, sampleInput("Item 3"));

        // Default maxBufferSize=0 → no eviction
        assertThat(buffer.pendingCount(KEY)).isEqualTo(3);
    }

    @Test
    @TestTransaction
    void drain_preservesInsertionOrder() {
        buffer.add(KEY, sampleInput("First"));
        buffer.add(KEY, sampleInput("Second"));
        buffer.add(KEY, sampleInput("Third"));

        var items = buffer.drain(KEY);
        assertThat(items).extracting(NotificationInput::title)
                .containsExactly("First", "Second", "Third");
    }

    @Test
    @TestTransaction
    void add_withEvictionEnabled_trimsOldest() {
        // Direct construction with maxBufferSize=3 to test eviction path
        var evictingBuffer = new JpaDigestBuffer(entityManager, 3);
        evictingBuffer.add(KEY, sampleInput("Item 1"));
        evictingBuffer.add(KEY, sampleInput("Item 2"));
        evictingBuffer.add(KEY, sampleInput("Item 3"));
        evictingBuffer.add(KEY, sampleInput("Item 4"));

        var items = evictingBuffer.drain(KEY);
        assertThat(items).hasSize(3);
        assertThat(items.get(0).title()).isEqualTo("Item 2");
    }

    @Test
    void concurrentDrainSkipsRowsClaimedByAnotherTransaction() throws Exception {
        buffer.add(KEY, sampleInput("Only once"));
        var drained = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> QuarkusTransaction.requiringNew().call(() -> {
                var items = buffer.drain(KEY);
                drained.countDown();
                if (!commit.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Commit not released");
                return items;
            }));
            try {
                assertThat(drained.await(10, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> buffer.drain(KEY));
                assertThat(second.get(5, TimeUnit.SECONDS)).isEmpty();
            } finally {
                commit.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS)).hasSize(1);
        }
        assertThat(buffer.pendingCount(KEY)).isZero();
    }

    @Test
    void interruptedDeliveryCanBeClaimedFromPersistentTracking() {
        buffer.add(KEY, sampleInput("Recover me"));
        var tracker = new DeliveryTracker(attempts, mapper, Duration.ofSeconds(30));
        var now = Instant.now();
        var prepared = buffer.drain(KEY, items -> tracker.preRecordDigest(KEY.channelId(),
                new DigestSummary(KEY.userId(), KEY.tenancyId(), KEY.channelId(), items, now, now, null),
                NotificationSeverity.INFO));

        // No confirmation: simulate stopping after the handoff and before delivery.
        assertThat(buffer.pendingCount(KEY)).isZero();
        assertThat(attempts.findById(prepared.id())).isNotNull();
        assertThat(attempts.claimRetryable(now.plus(Duration.ofMinutes(6)), 10))
                .extracting(a -> a.id()).containsExactly(prepared.id());
    }

    @Test
    void failedHandoffRollsBackBothTrackingAndBufferRemoval() {
        buffer.add(KEY, sampleInput("Keep me"));
        var tracker = new DeliveryTracker(attempts, mapper, Duration.ofSeconds(30));
        var now = Instant.now();
        var attemptId = new AtomicReference<String>();

        assertThatThrownBy(() -> buffer.drain(KEY, items -> {
            var prepared = tracker.preRecordDigest(KEY.channelId(),
                    new DigestSummary(KEY.userId(), KEY.tenancyId(), KEY.channelId(), items, now, now, null),
                    NotificationSeverity.INFO);
            attemptId.set(prepared.id());
            entityManager.flush();
            throw new IllegalStateException("Interrupted before handoff commit");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(buffer.pendingCount(KEY)).isEqualTo(1);
        assertThat(attempts.findById(attemptId.get())).isNull();
    }

    private static NotificationInput sampleInput(String title) {
        return new NotificationInput("user-1", "tenant-1", title, null, "test",
                NotificationSeverity.INFO, null,
                new NotificationSource(UUID.randomUUID().toString(), "work-item", "wi-1", "actor-1"), null);
    }
}
