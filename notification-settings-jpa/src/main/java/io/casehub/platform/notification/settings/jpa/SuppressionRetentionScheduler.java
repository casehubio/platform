package io.casehub.platform.notification.settings.jpa;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;

/**
 * Scheduled cleanup of expired mute rules and snooze records.
 *
 * <p>Runs daily at 02:00 to purge entries where {@code expiresAt} (mute rules)
 * or {@code until} (snooze) is in the past. Per the store-owned-retention-mechanism
 * protocol, JPA stores own their retention policy.
 */
@ApplicationScoped
public class SuppressionRetentionScheduler {

    @Inject
    EntityManager entityManager;

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    void purgeExpiredSuppressionData() {
        Instant now = Instant.now();

        int deletedMutes = entityManager.createQuery(
                        "DELETE FROM MuteRuleEntity m WHERE m.expiresAt IS NOT NULL AND m.expiresAt < :now")
                .setParameter("now", now)
                .executeUpdate();

        int deletedSnooze = entityManager.createQuery(
                        "DELETE FROM SnoozeEntity s WHERE s.until < :now")
                .setParameter("now", now)
                .executeUpdate();

        if (deletedMutes > 0 || deletedSnooze > 0) {
            Log.infof("Suppression retention purge: %d expired mutes, %d expired snooze", deletedMutes, deletedSnooze);
        }
    }
}
