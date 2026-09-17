package io.casehub.platform.notification.settings.spring.jpa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class SuppressionRetentionScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(SuppressionRetentionScheduler.class);

    private final MuteRuleEntityRepository muteRepo;
    private final SnoozeEntityRepository snoozeRepo;

    public SuppressionRetentionScheduler(MuteRuleEntityRepository muteRepo, SnoozeEntityRepository snoozeRepo) {
        this.muteRepo = muteRepo;
        this.snoozeRepo = snoozeRepo;
    }

    @Scheduled(cron = "${casehub.notification.suppression.retention-purge-cron:0 0 2 * * ?}")
    @Transactional
    public void purgeExpiredSuppressionData() {
        Instant now = Instant.now();
        int deletedMutes = muteRepo.deleteExpired(now);
        int deletedSnooze = snoozeRepo.deleteExpired(now);
        if (deletedMutes > 0 || deletedSnooze > 0) {
            LOG.info("Suppression retention purge: {} expired mutes, {} expired snooze", deletedMutes, deletedSnooze);
        }
    }
}
