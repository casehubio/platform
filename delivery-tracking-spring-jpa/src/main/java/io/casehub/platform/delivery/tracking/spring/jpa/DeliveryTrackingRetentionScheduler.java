package io.casehub.platform.delivery.tracking.spring.jpa;

import io.casehub.platform.api.delivery.DeliverySourceType;
import io.casehub.platform.api.delivery.DeliveryStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
public class DeliveryTrackingRetentionScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DeliveryTrackingRetentionScheduler.class);

    private final DeliveryAttemptEntityRepository attemptRepo;
    private final EngagementEventEntityRepository engagementRepo;
    private final int attemptRetentionDays;
    private final int failedRetentionDays;
    private final int engagementRetentionDays;
    private final Duration claimTimeout;

    public DeliveryTrackingRetentionScheduler(
            DeliveryAttemptEntityRepository attemptRepo,
            EngagementEventEntityRepository engagementRepo,
            @Value("${casehub.delivery.retention.attempt-days:90}") int attemptRetentionDays,
            @Value("${casehub.delivery.retention.failed-attempt-days:30}") int failedRetentionDays,
            @Value("${casehub.delivery.retention.engagement-days:365}") int engagementRetentionDays,
            @Value("${casehub.delivery.retry.claim-timeout:5m}") Duration claimTimeout) {
        this.attemptRepo = attemptRepo;
        this.engagementRepo = engagementRepo;
        this.attemptRetentionDays = attemptRetentionDays;
        this.failedRetentionDays = failedRetentionDays;
        this.engagementRetentionDays = engagementRetentionDays;
        this.claimTimeout = claimTimeout;
    }

    @Scheduled(cron = "${casehub.delivery.retention.attempt-purge-cron:0 0 3 * * ?}")
    @Transactional
    public void attemptRetentionPurge() {
        for (DeliverySourceType sourceType : DeliverySourceType.values()) {
            Instant attemptCutoff = Instant.now().minus(Duration.ofDays(attemptRetentionDays));
            Instant failedCutoff = Instant.now().minus(Duration.ofDays(failedRetentionDays));

            int purged = 0;
            purged += attemptRepo.deleteBySourceTypeAndStatusBefore(sourceType, DeliveryStatus.DELIVERED, attemptCutoff);
            purged += attemptRepo.deleteBySourceTypeAndStatusBefore(sourceType, DeliveryStatus.EXPIRED, attemptCutoff);
            purged += attemptRepo.deleteBySourceTypeAndStatusBefore(sourceType, DeliveryStatus.FAILED, failedCutoff);
            purged += attemptRepo.deleteStaleRetrying(sourceType, DeliveryStatus.RETRYING, attemptCutoff);

            if (purged > 0) {
                LOG.info("Attempt retention purge [{}]: {} records removed", sourceType, purged);
            }
        }
        int orphaned = attemptRepo.deleteOrphanedPrePersist(
                DeliveryStatus.RETRYING, Instant.now().minus(claimTimeout));
        if (orphaned > 0) {
            LOG.info("Orphaned pre-persist purge: {} records removed", orphaned);
        }
    }

    @Scheduled(cron = "${casehub.delivery.retention.engagement-purge-cron:0 30 3 * * ?}")
    @Transactional
    public void engagementRetentionPurge() {
        for (DeliverySourceType sourceType : DeliverySourceType.values()) {
            Instant cutoff = Instant.now().minus(Duration.ofDays(engagementRetentionDays));
            int purged = engagementRepo.deleteBySourceTypeBefore(sourceType, cutoff);
            if (purged > 0) {
                LOG.info("Engagement retention purge [{}]: {} records removed", sourceType, purged);
            }
        }
    }
}
