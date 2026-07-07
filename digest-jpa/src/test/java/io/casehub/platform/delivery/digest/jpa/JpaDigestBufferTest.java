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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class JpaDigestBufferTest {

    @Inject
    DigestBuffer buffer;

    @Inject
    EntityManager entityManager;

    private static final DigestBufferKey KEY = new DigestBufferKey("user-1", "tenant-1", "email");

    @BeforeEach
    @TestTransaction
    void setUp() {
        entityManager.createQuery("DELETE FROM DigestBufferEntity").executeUpdate();
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

    private static NotificationInput sampleInput(String title) {
        return new NotificationInput("user-1", "tenant-1", title, null, "test",
                NotificationSeverity.INFO, null,
                new NotificationSource(UUID.randomUUID().toString(), "work-item", "wi-1", "actor-1"));
    }
}
