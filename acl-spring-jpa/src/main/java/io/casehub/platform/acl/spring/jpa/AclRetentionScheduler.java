package io.casehub.platform.acl.spring.jpa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
public class AclRetentionScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(AclRetentionScheduler.class);

    private final AclEntryEntityRepository entryRepo;
    private final AclAuditLogEntityRepository auditRepo;
    private final int auditRetentionDays;

    public AclRetentionScheduler(
            AclEntryEntityRepository entryRepo,
            AclAuditLogEntityRepository auditRepo,
            @Value("${casehub.acl.retention.audit-days:365}") int auditRetentionDays) {
        this.entryRepo = entryRepo;
        this.auditRepo = auditRepo;
        this.auditRetentionDays = auditRetentionDays;
    }

    @Scheduled(cron = "${casehub.acl.retention.expired-purge-cron:0 0 3 * * ?}")
    @Transactional
    public void purgeExpiredEntries() {
        int purged = entryRepo.deleteExpired(Instant.now());
        if (purged > 0) {
            LOG.info("ACL expired entry purge: {} entries removed", purged);
        }
    }

    @Scheduled(cron = "${casehub.acl.retention.audit-purge-cron:0 30 3 * * ?}")
    @Transactional
    public void purgeAuditLog() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(auditRetentionDays));
        int purged = auditRepo.deleteBefore(cutoff);
        if (purged > 0) {
            LOG.info("ACL audit log purge: {} entries removed", purged);
        }
    }
}
