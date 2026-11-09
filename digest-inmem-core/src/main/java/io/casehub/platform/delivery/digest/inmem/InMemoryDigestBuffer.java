package io.casehub.platform.delivery.digest.inmem;

import io.casehub.platform.api.delivery.DigestBuffer;
import io.casehub.platform.api.delivery.DigestBufferKey;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.preferences.PlatformPreferenceKeys;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.SettingsScope;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class InMemoryDigestBuffer implements DigestBuffer {

    private static final Logger LOG = Logger.getLogger(InMemoryDigestBuffer.class);

    private final ConcurrentHashMap<DigestBufferKey, BufferEntry> buffers   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<DigestBufferKey>> userIndex = new ConcurrentHashMap<>();
    private final int                maxBufferSize;
    private final PreferenceProvider preferenceProvider;
    private final long               fixedRetentionMs;

    public InMemoryDigestBuffer(int maxBufferSize, PreferenceProvider preferenceProvider) {
        this.maxBufferSize      = maxBufferSize;
        this.preferenceProvider = preferenceProvider;
        this.fixedRetentionMs   = -1;
    }

    public InMemoryDigestBuffer(int maxBufferSize, long fixedRetentionMs) {
        this.maxBufferSize      = maxBufferSize;
        this.preferenceProvider = null;
        this.fixedRetentionMs   = fixedRetentionMs;
    }

    private static String userKey(String userId, String tenancyId) {
        return userId + "|" + tenancyId;
    }

    private boolean isExpired(BufferEntry entry) {
        long retentionMs = retentionMs();
        return retentionMs > 0 && entry.lastModified().toEpochMilli() + retentionMs < System.currentTimeMillis();
    }

    private long retentionMs() {
        if (fixedRetentionMs >= 0) {
            return fixedRetentionMs;
        }
        int days = preferenceProvider
                .resolve(SettingsScope.root(TenancyConstants.PLATFORM_TENANT_ID))
                .getOrDefault(PlatformPreferenceKeys.DIGEST_RETENTION_DAYS)
                .value();
        return days * 86_400_000L;
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
        return drain(key, Function.identity());
    }

    /**
     * Removes the entry before running {@code transfer} so that the transfer never runs while
     * a map bin lock is held (it may perform I/O or touch this buffer). A concurrent drain of
     * the same key sees an empty buffer, mirroring {@code SKIP LOCKED} in the JPA adapters.
     * If the transfer fails, drained items are restored ahead of any items added meanwhile.
     */
    @Override
    public <T> T drain(DigestBufferKey key, Function<List<NotificationInput>, T> transfer) {
        var entry = buffers.remove(key);
        if (entry != null) {
            removeFromUserIndex(key);
        }
        List<NotificationInput> items = entry == null || isExpired(entry) ? List.of()
                : List.copyOf(entry.notifications());
        try {
            return transfer.apply(items);
        } catch (RuntimeException | Error e) {
            if (!items.isEmpty()) {
                restore(key, entry);
            }
            throw e;
        }
    }

    private void restore(DigestBufferKey key, BufferEntry drained) {
        buffers.merge(key, drained, (current, restored) -> {
            var merged = new CopyOnWriteArrayList<>(restored.notifications());
            merged.addAll(current.notifications());
            while (merged.size() > maxBufferSize) {
                merged.remove(0);
            }
            return new BufferEntry(merged, restored.firstAdded(), current.lastModified());
        });
        userIndex.computeIfAbsent(userKey(key.userId(), key.tenancyId()), k -> ConcurrentHashMap.newKeySet())
                .add(key);
    }

    private void removeFromUserIndex(DigestBufferKey key) {
        userIndex.computeIfPresent(userKey(key.userId(), key.tenancyId()), (k, set) -> {
            set.remove(key);
            return set.isEmpty() ? null : set;
        });
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
