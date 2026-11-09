package io.casehub.platform.delivery.digest.jpa;

import io.casehub.platform.api.delivery.DigestBuffer;
import io.casehub.platform.api.delivery.DigestBufferKey;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.preferences.PlatformPreferenceKeys;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.SettingsScope;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@ApplicationScoped
public class JpaDigestBuffer implements DigestBuffer {

    private static final Logger LOG = Logger.getLogger(JpaDigestBuffer.class);

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "casehub.notification.digest.max-buffer-size", defaultValue = "0")
    int maxBufferSize;
    @Inject
    PreferenceProvider preferenceProvider;


    // CDI no-arg constructor
    public JpaDigestBuffer() {
    }

    // Package-private constructor for test with explicit maxBufferSize
    JpaDigestBuffer(EntityManager entityManager, int maxBufferSize) {
        this.entityManager = entityManager;
        this.maxBufferSize = maxBufferSize;
    }

    @Override
    @Transactional
    public void add(DigestBufferKey key, NotificationInput notification) {
        var entity = DigestBufferEntity.fromNotificationInput(
                key.userId(), key.tenancyId(), key.channelId(), notification);
        entityManager.persist(entity);

        if (maxBufferSize > 0) {
            long count = entityManager.createQuery(
                            "SELECT COUNT(e) FROM DigestBufferEntity e " +
                                    "WHERE e.userId = :userId AND e.tenancyId = :tenancyId " +
                                    "AND e.channelId = :channelId", Long.class)
                    .setParameter("userId", key.userId())
                    .setParameter("tenancyId", key.tenancyId())
                    .setParameter("channelId", key.channelId())
                    .getSingleResult();

            if (count > maxBufferSize) {
                long excess = count - maxBufferSize;
                List<UUID> toDelete = entityManager.createQuery(
                                "SELECT e.id FROM DigestBufferEntity e " +
                                        "WHERE e.userId = :userId AND e.tenancyId = :tenancyId " +
                                        "AND e.channelId = :channelId ORDER BY e.bufferedAt ASC", UUID.class)
                        .setParameter("userId", key.userId())
                        .setParameter("tenancyId", key.tenancyId())
                        .setParameter("channelId", key.channelId())
                        .setMaxResults((int) excess)
                        .getResultList();

                entityManager.createQuery(
                                "DELETE FROM DigestBufferEntity e WHERE e.id IN :ids")
                        .setParameter("ids", toDelete)
                        .executeUpdate();

                LOG.debugf("Buffer eviction for key %s — %d rows trimmed", key, excess);
            }
        }
    }

    @Override
    @Transactional
    public List<NotificationInput> drain(DigestBufferKey key) {
        return drain(key, Function.identity());
    }

    @Override
    @Transactional
    public <T> T drain(DigestBufferKey key, Function<List<NotificationInput>, T> transfer) {
        List<DigestBufferEntity> entities = entityManager.createQuery(
                        "SELECT e FROM DigestBufferEntity e " +
                                "WHERE e.userId = :userId AND e.tenancyId = :tenancyId " +
                                "AND e.channelId = :channelId ORDER BY e.bufferedAt ASC",
                        DigestBufferEntity.class)
                .setParameter("userId", key.userId())
                .setParameter("tenancyId", key.tenancyId())
                .setParameter("channelId", key.channelId())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint("jakarta.persistence.lock.timeout", -2)
                .getResultList();

        var result = transfer.apply(entities.stream().map(DigestBufferEntity::toNotificationInput).toList());
        if (entities.isEmpty()) {
            return result;
        }

        List<UUID> ids = entities.stream().map(e -> e.id).toList();
        entityManager.createQuery(
                        "DELETE FROM DigestBufferEntity e WHERE e.id IN :ids")
                .setParameter("ids", ids)
                .executeUpdate();

        return result;
    }

    @Override
    public Set<DigestBufferKey> pendingKeys() {
        List<Object[]> rows = entityManager.createQuery(
                        "SELECT DISTINCT e.userId, e.tenancyId, e.channelId " +
                                "FROM DigestBufferEntity e", Object[].class)
                .getResultList();

        Set<DigestBufferKey> keys = new HashSet<>();
        for (Object[] row : rows) {
            keys.add(new DigestBufferKey((String) row[0], (String) row[1], (String) row[2]));
        }
        return keys;
    }

    @Override
    public Optional<Instant> oldestPendingTimestamp(DigestBufferKey key) {
        Instant oldest = entityManager.createQuery(
                        "SELECT MIN(e.bufferedAt) FROM DigestBufferEntity e " +
                                "WHERE e.userId = :userId AND e.tenancyId = :tenancyId " +
                                "AND e.channelId = :channelId", Instant.class)
                .setParameter("userId", key.userId())
                .setParameter("tenancyId", key.tenancyId())
                .setParameter("channelId", key.channelId())
                .getSingleResult();
        return Optional.ofNullable(oldest);
    }

    @Override
    public int pendingCount(DigestBufferKey key) {
        Long count = entityManager.createQuery(
                        "SELECT COUNT(e) FROM DigestBufferEntity e " +
                                "WHERE e.userId = :userId AND e.tenancyId = :tenancyId " +
                                "AND e.channelId = :channelId", Long.class)
                .setParameter("userId", key.userId())
                .setParameter("tenancyId", key.tenancyId())
                .setParameter("channelId", key.channelId())
                .getSingleResult();
        return count.intValue();
    }

    @Override
    public Set<DigestBufferKey> pendingKeysForUser(String userId, String tenancyId) {
        List<Object[]> rows = entityManager.createQuery(
                        "SELECT DISTINCT e.userId, e.tenancyId, e.channelId " +
                                "FROM DigestBufferEntity e " +
                                "WHERE e.userId = :userId AND e.tenancyId = :tenancyId",
                        Object[].class)
                .setParameter("userId", userId)
                .setParameter("tenancyId", tenancyId)
                .getResultList();

        Set<DigestBufferKey> keys = new HashSet<>();
        for (Object[] row : rows) {
            keys.add(new DigestBufferKey((String) row[0], (String) row[1], (String) row[2]));
        }
        return keys;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    void retentionPurge() {
        int retentionDays = preferenceProvider
                .resolve(SettingsScope.root(TenancyConstants.PLATFORM_TENANT_ID))
                .getOrDefault(PlatformPreferenceKeys.DIGEST_RETENTION_DAYS)
                .value();
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        int purged = entityManager.createQuery(
                                          "DELETE FROM DigestBufferEntity e WHERE e.bufferedAt < :cutoff " +
                                          "AND NOT EXISTS (" +
                                          "  SELECT 1 FROM DigestBufferEntity recent " +
                                          "  WHERE recent.userId = e.userId " +
                                          "  AND recent.tenancyId = e.tenancyId " +
                                          "  AND recent.channelId = e.channelId " +
                                          "  AND recent.bufferedAt >= :cutoff" +
                                          ")")
                                  .setParameter("cutoff", cutoff)
                                  .executeUpdate();
        if (purged > 0) {
            LOG.infof("Digest retention purge: %d orphan rows removed", purged);
        }
    }
}
