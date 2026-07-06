package io.casehub.platform.api.delivery;

import io.casehub.platform.api.notification.NotificationInput;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DigestBuffer {
    void add(DigestBufferKey key, NotificationInput notification);
    List<NotificationInput> drain(DigestBufferKey key);
    Set<DigestBufferKey> pendingKeys();
    Optional<Instant> oldestPendingTimestamp(DigestBufferKey key);
}
