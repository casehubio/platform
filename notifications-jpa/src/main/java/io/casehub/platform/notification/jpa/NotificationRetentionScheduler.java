package io.casehub.platform.notification.jpa;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.notification.NotificationStatus;
import io.casehub.platform.api.preferences.PlatformPreferenceKeys;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.SettingsScope;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class NotificationRetentionScheduler {

    private static final Logger LOG = Logger.getLogger(NotificationRetentionScheduler.class);

    @Inject
    EntityManager em;

    @Inject
    PreferenceProvider preferenceProvider;

    @Scheduled(every = "${casehub.notification.jpa.retention-check-interval:24h}")
    @Transactional
    void purge() {
        var prefs               = preferenceProvider.resolve(SettingsScope.root(TenancyConstants.PLATFORM_TENANT_ID));
        int retentionDays       = prefs.getOrDefault(PlatformPreferenceKeys.NOTIFICATION_RETENTION_DAYS).value();
        int unreadRetentionDays = prefs.getOrDefault(PlatformPreferenceKeys.NOTIFICATION_UNREAD_RETENTION_DAYS).value();

        Instant readDismissedCutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        Instant unreadCutoff        = Instant.now().minus(unreadRetentionDays, ChronoUnit.DAYS);

        LOG.infof("Starting notification retention purge: READ/DISMISSED < %s, UNREAD < %s",
                  readDismissedCutoff, unreadCutoff);

        int readDismissed = em.createQuery(
                                      "DELETE FROM NotificationEntity " +
                                      "WHERE status IN (:read, :dismissed) AND createdAt < :cutoff")
                              .setParameter("read", NotificationStatus.READ)
                              .setParameter("dismissed", NotificationStatus.DISMISSED)
                              .setParameter("cutoff", readDismissedCutoff)
                              .executeUpdate();

        int unread = em.createQuery(
                               "DELETE FROM NotificationEntity " +
                               "WHERE status = :unread AND createdAt < :cutoff")
                       .setParameter("unread", NotificationStatus.UNREAD)
                       .setParameter("cutoff", unreadCutoff)
                       .executeUpdate();

        LOG.infof("Notification retention purge completed: %d notifications deleted",
                  readDismissed + unread);
    }
}
