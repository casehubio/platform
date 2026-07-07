package io.casehub.platform.delivery.digest.inmem;

import io.casehub.platform.api.delivery.DigestBuffer;
import io.casehub.platform.api.delivery.DigestBufferKey;
import io.casehub.platform.api.notification.NotificationInput;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Alternative
@Priority(100)
@ApplicationScoped
public class InMemoryDigestBuffer implements DigestBuffer {

    private static final Logger LOG = Logger.getLogger(InMemoryDigestBuffer.class);

    private final ConcurrentHashMap<DigestBufferKey, BufferEntry> buffers = new ConcurrentHashMap<>();
    private final int maxBufferSize;

    public InMemoryDigestBuffer(
            @ConfigProperty(name = "casehub.notification.digest.max-buffer-size", defaultValue = "500")
            int maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
    }

    @Override
    public void add(DigestBufferKey key, NotificationInput notification) {
        buffers.compute(key, (k, entry) -> {
            if (entry == null) {
                var list = new CopyOnWriteArrayList<NotificationInput>();
                list.add(notification);
                return new BufferEntry(list, Instant.now());
            }
            entry.notifications().add(notification);
            if (entry.notifications().size() > maxBufferSize) {
                entry.notifications().remove(0);
                LOG.debugf("Buffer eviction for key %s — max size %d exceeded", key, maxBufferSize);
            }
            return entry;
        });
    }

    @Override
    public List<NotificationInput> drain(DigestBufferKey key) {
        var entry = buffers.remove(key);
        return entry != null ? List.copyOf(entry.notifications()) : List.of();
    }

    @Override
    public Set<DigestBufferKey> pendingKeys() {
        return Set.copyOf(buffers.keySet());
    }

    @Override
    public Optional<Instant> oldestPendingTimestamp(DigestBufferKey key) {
        var entry = buffers.get(key);
        return entry != null ? Optional.of(entry.firstAdded()) : Optional.empty();
    }

    @Override
    public int pendingCount(DigestBufferKey key) {
        var entry = buffers.get(key);
        return entry != null ? entry.notifications().size() : 0;
    }

    @Override
    public Set<DigestBufferKey> pendingKeysForUser(String userId, String tenancyId) {
        return buffers.keySet().stream()
                .filter(key -> key.userId().equals(userId) && key.tenancyId().equals(tenancyId))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    record BufferEntry(CopyOnWriteArrayList<NotificationInput> notifications, Instant firstAdded) {}
}
