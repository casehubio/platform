package io.casehub.platform.delivery.digest.spring.jpa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
public class DigestRetentionScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DigestRetentionScheduler.class);

    private final DigestBufferEntityRepository repo;
    private final int retentionDays;

    public DigestRetentionScheduler(
            DigestBufferEntityRepository repo,
            @Value("${casehub.notification.digest.retention-days:30}") int retentionDays) {
        this.repo = repo;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${casehub.notification.digest.retention-purge-cron:0 0 3 * * ?}")
    @Transactional
    public void retentionPurge() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        int purged = repo.deleteOrphansBefore(cutoff);
        if (purged > 0) {
            LOG.info("Digest retention purge: {} orphan rows removed", purged);
        }
    }
}
