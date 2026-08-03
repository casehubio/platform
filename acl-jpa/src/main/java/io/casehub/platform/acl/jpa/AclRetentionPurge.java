package io.casehub.platform.acl.jpa;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.preferences.PlatformPreferenceKeys;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.SettingsScope;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class AclRetentionPurge {

    private static final Logger LOG = Logger.getLogger(AclRetentionPurge.class);

    @Inject
    EntityManager entityManager;

    @Inject
    PreferenceProvider preferenceProvider;

    @Scheduled(cron = "${casehub.acl.retention.expired-purge-cron:0 0 3 * * ?}")
    @Transactional
    void purgeExpiredEntries() {
        int purged = entityManager.createQuery(
                                          "DELETE FROM AclEntryEntity e " +
                                          "WHERE e.expiresAt IS NOT NULL AND e.expiresAt < :now")
                                  .setParameter("now", Instant.now())
                                  .executeUpdate();
        if (purged > 0) {
            LOG.infof("ACL expired entry purge: %d records removed", purged);
        }
    }

    @Scheduled(cron = "${casehub.acl.retention.audit-purge-cron:0 30 3 * * ?}")
    @Transactional
    void purgeAuditLog() {
        int auditRetentionDays = preferenceProvider.resolve(SettingsScope.root(TenancyConstants.PLATFORM_TENANT_ID))
                                                   .getOrDefault(PlatformPreferenceKeys.ACL_AUDIT_RETENTION_DAYS).value();

        Instant cutoff = Instant.now().minus(Duration.ofDays(auditRetentionDays));
        int purged = entityManager.createQuery(
                                          "DELETE FROM AclAuditLogEntity e " +
                                          "WHERE e.performedAt < :cutoff")
                                  .setParameter("cutoff", cutoff)
                                  .executeUpdate();
        if (purged > 0) {
            LOG.infof("ACL audit log purge: %d records removed (older than %d days)", purged, auditRetentionDays);
        }
    }
}
