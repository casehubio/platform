package io.casehub.platform.delivery.digest.spring.jpa;

import io.casehub.platform.api.delivery.DigestBuffer;
import io.casehub.platform.api.delivery.DigestBufferKey;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.delivery.digest.jpa.DigestBufferEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SpringDigestBuffer implements DigestBuffer {

    private static final Logger LOG = LoggerFactory.getLogger(SpringDigestBuffer.class);

    private final DigestBufferEntityRepository repo;
    private final int maxBufferSize;

    public SpringDigestBuffer(DigestBufferEntityRepository repo, int maxBufferSize) {
        this.repo = repo;
        this.maxBufferSize = maxBufferSize;
    }

    @Override
    @Transactional
    public void add(DigestBufferKey key, NotificationInput notification) {
        var entity = DigestBufferEntity.fromNotificationInput(
                key.userId(), key.tenancyId(), key.channelId(), notification);
        repo.save(entity);

        if (maxBufferSize > 0) {
            long count = repo.countByUserIdAndTenancyIdAndChannelId(
                    key.userId(), key.tenancyId(), key.channelId());
            if (count > maxBufferSize) {
                long excess = count - maxBufferSize;
                List<UUID> oldestIds = repo.findOldestIds(
                        key.userId(), key.tenancyId(), key.channelId());
                List<UUID> toDelete = oldestIds.subList(0, (int) Math.min(excess, oldestIds.size()));
                repo.deleteByIds(toDelete);
                LOG.debug("Buffer eviction for key {} — {} rows trimmed", key, excess);
            }
        }
    }

    @Override
    @Transactional
    public List<NotificationInput> drain(DigestBufferKey key) {
        List<DigestBufferEntity> entities = repo
                .findByUserIdAndTenancyIdAndChannelIdOrderByBufferedAtAsc(
                        key.userId(), key.tenancyId(), key.channelId());
        if (entities.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = entities.stream().map(e -> e.id).toList();
        repo.deleteByIds(ids);

        return entities.stream()
                .map(DigestBufferEntity::toNotificationInput)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<DigestBufferKey> pendingKeys() {
        List<Object[]> rows = repo.findDistinctKeys();
        Set<DigestBufferKey> keys = new HashSet<>();
        for (Object[] row : rows) {
            keys.add(new DigestBufferKey((String) row[0], (String) row[1], (String) row[2]));
        }
        return keys;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> oldestPendingTimestamp(DigestBufferKey key) {
        Instant oldest = repo.findOldestTimestamp(key.userId(), key.tenancyId(), key.channelId());
        return Optional.ofNullable(oldest);
    }

    @Override
    @Transactional(readOnly = true)
    public int pendingCount(DigestBufferKey key) {
        return (int) repo.countByUserIdAndTenancyIdAndChannelId(
                key.userId(), key.tenancyId(), key.channelId());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<DigestBufferKey> pendingKeysForUser(String userId, String tenancyId) {
        List<Object[]> rows = repo.findDistinctKeysByUser(userId, tenancyId);
        Set<DigestBufferKey> keys = new HashSet<>();
        for (Object[] row : rows) {
            keys.add(new DigestBufferKey((String) row[0], (String) row[1], (String) row[2]));
        }
        return keys;
    }
}
