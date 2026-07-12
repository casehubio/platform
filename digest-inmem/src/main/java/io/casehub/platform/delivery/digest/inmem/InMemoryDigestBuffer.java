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

    private final ConcurrentHashMap<DigestBufferKey, BufferEntry> buffers   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<DigestBufferKey>> userIndex = new ConcurrentHashMap<>();
    private final int                                             maxBufferSize;
    private final long                                            retentionMs;

    public InMemoryDigestBuffer(
            @ConfigProperty(name = "casehub.notification.digest.max-buffer-size", defaultValue = "500")
            int maxBufferSize,
            @ConfigProperty(name = "casehub.notification.digest.retention-days", defaultValue = "90")
            int retentionDays) {
        this.maxBufferSize = maxBufferSize;
        this.retentionMs   = retentionDays * 86_400_000L;
    }

    InMemoryDigestBuffer(int maxBufferSize, long retentionMs) {
        this.maxBufferSize = maxBufferSize;
        this.retentionMs   = retentionMs;
    }

    private static String userKey(String userId, String tenancyId) {
        return userId + "|" + tenancyId;
    }

    private boolean isExpired(BufferEntry entry) {
        return retentionMs > 0 && entry.lastModified().toEpochMilli() + retentionMs < System.currentTimeMillis();
    }

    @Override
    public void add(DigestBufferKey key, NotificationInput notification) {
        buffers.compute(key, (k, entry) -> {
            if (entry == null) {
                var list = new CopyOnWriteArrayList<NotificationInput>();
                list.add(notification);
                return new BufferEntry(list, Instant.now(), Instant.now());
            }
            entry.notifications().add(notification);
            if (entry.notifications().size() > maxBufferSize) {
                entry.notifications().remove(0);
                LOG.debugf("Buffer eviction for key %s — max size %d exceeded", key, maxBufferSize);
            }
            return new BufferEntry(entry.notifications(), entry.firstAdded(), Instant.now());
        });
        userIndex.compute(userKey(key.userId(), key.tenancyId()), (k, set) -> {
            if (set == null) {set = ConcurrentHashMap.newKeySet();}
            set.add(key);
            return set;
        });
    }

    @Override
    public List<NotificationInput> drain(DigestBufferKey key) {
        var entry = buffers.remove(key);
        if (entry != null) {
            userIndex.computeIfPresent(userKey(key.userId(), key.tenancyId()), (k, set) -> {
                set.remove(key);
                return set.isEmpty() ? null : set;
            });
            if (isExpired(entry)) {
                return List.of();
            }
        }
        return entry != null ? List.copyOf(entry.notifications()) : List.of();
    }

    @Override
    public Set<DigestBufferKey> pendingKeys() {
        var keys = new java.util.HashSet<DigestBufferKey>();
        for (var e : buffers.entrySet()) {
            if (isExpired(e.getValue())) {
                if (buffers.remove(e.getKey(), e.getValue())) {
                    userIndex.computeIfPresent(userKey(e.getKey().userId(), e.getKey().tenancyId()), (k, set) -> {
                        set.remove(e.getKey());
                        return set.isEmpty() ? null : set;
                    });
                }
            } else {
                keys.add(e.getKey());
            }
        }
        return Set.copyOf(keys);
    }

    @Override
    public Optional<Instant> oldestPendingTimestamp(DigestBufferKey key) {
        var entry = buffers.get(key);
        if (entry != null && isExpired(entry)) {
            if (buffers.remove(key, entry)) {
                userIndex.computeIfPresent(userKey(key.userId(), key.tenancyId()), (k, set) -> {
                    set.remove(key);
                    return set.isEmpty() ? null : set;
                });
            }
            return Optional.empty();
        }
        return entry != null ? Optional.of(entry.firstAdded()) : Optional.empty();
    }

    @Override
    public int pendingCount(DigestBufferKey key) {
        var entry = buffers.get(key);
        if (entry != null && isExpired(entry)) {
            if (buffers.remove(key, entry)) {
                userIndex.computeIfPresent(userKey(key.userId(), key.tenancyId()), (k, set) -> {
                    set.remove(key);
                    return set.isEmpty() ? null : set;
                });
            }
            return 0;
        }
        return entry != null ? entry.notifications().size() : 0;
    }

    @Override
    public Set<DigestBufferKey> pendingKeysForUser(String userId, String tenancyId) {
        var set = userIndex.get(userKey(userId, tenancyId));
        if (set == null) {return Set.of();}
        var result = new java.util.HashSet<DigestBufferKey>();
        for (DigestBufferKey key : set) {
            var entry = buffers.get(key);
            if (entry != null && !isExpired(entry)) {
                result.add(key);
            }
        }
        return Set.copyOf(result);
    }

    record BufferEntry(CopyOnWriteArrayList<NotificationInput> notifications, Instant firstAdded,
                       Instant lastModified) {}
}
