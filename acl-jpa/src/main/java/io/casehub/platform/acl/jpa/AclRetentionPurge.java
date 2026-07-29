package io.casehub.platform.acl.jpa;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class AclRetentionPurge {

    private static final Logger LOG = Logger.getLogger(AclRetentionPurge.class);

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "casehub.acl.retention.audit-days", defaultValue = "365")
    int auditRetentionDays;

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
