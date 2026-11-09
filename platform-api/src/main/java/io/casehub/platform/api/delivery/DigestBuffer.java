package io.casehub.platform.api.delivery;

import io.casehub.platform.api.notification.NotificationInput;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Temporary buffer for notifications awaiting digest delivery.
 *
 * <p>Thread-safe. Implementations must use atomic operations to avoid lost updates
 * during concurrent add/drain.
 */
public interface DigestBuffer {
    /** Add a notification to the buffer. Thread-safe. */
    void add(DigestBufferKey key, NotificationInput notification);

    /**
     * Atomically drain all buffered notifications for the key.
     * Returns an immutable list; the buffer for this key is empty after the call.
     * Concurrent adds during drain must not lose items — implementations must
     * use atomic swap (e.g. ConcurrentHashMap.remove()) not clear().
     */
    List<NotificationInput> drain(DigestBufferKey key);

    /**
     * Transfer drained items before returning them to the caller. If transfer fails,
     * retain the items. Persistent implementations must run transfer and removal in
     * the same transaction; the transfer must enlist in that transaction as well.
     * The default implementation restores items on failure for volatile adapters.
     */
    default <T> T drain(DigestBufferKey key, Function<List<NotificationInput>, T> transfer) {
        var items = drain(key);
        try {
            return transfer.apply(items);
        } catch (RuntimeException | Error e) {
            items.forEach(item -> add(key, item));
            throw e;
        }
    }

    /** Returns a snapshot of keys with pending items. Thread-safe. */
    Set<DigestBufferKey> pendingKeys();

    /** Timestamp of the oldest buffered item for the key, or empty if no items. */
    Optional<Instant> oldestPendingTimestamp(DigestBufferKey key);

    /** Count of pending items for the key without draining. */
    int pendingCount(DigestBufferKey key);

    /** Pending keys for a specific user. Avoids full-scan in per-user REST endpoints. */
    Set<DigestBufferKey> pendingKeysForUser(String userId, String tenancyId);
}
