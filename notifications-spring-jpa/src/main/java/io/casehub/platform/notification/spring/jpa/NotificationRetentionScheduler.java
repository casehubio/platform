package io.casehub.platform.notification.spring.jpa;

import io.casehub.platform.api.notification.NotificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class NotificationRetentionScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationRetentionScheduler.class);

    private final NotificationEntityRepository repo;
    private final int readRetentionDays;
    private final int unreadRetentionDays;

    public NotificationRetentionScheduler(
            NotificationEntityRepository repo,
            @Value("${casehub.notification.retention.read-days:90}") int readRetentionDays,
            @Value("${casehub.notification.retention.unread-days:365}") int unreadRetentionDays) {
        this.repo = repo;
        this.readRetentionDays = readRetentionDays;
        this.unreadRetentionDays = unreadRetentionDays;
    }

    @Scheduled(cron = "${casehub.notification.retention.purge-cron:0 0 3 * * ?}")
    @Transactional
    public void retentionPurge() {
        Instant readCutoff = Instant.now().minus(Duration.ofDays(readRetentionDays));
        int readPurged = repo.deleteByStatusAndCreatedBefore(
                List.of(NotificationStatus.READ, NotificationStatus.DISMISSED), readCutoff);

        Instant unreadCutoff = Instant.now().minus(Duration.ofDays(unreadRetentionDays));
        int unreadPurged = repo.deleteByStatusAndCreatedBefore(
                List.of(NotificationStatus.UNREAD), unreadCutoff);

        if (readPurged + unreadPurged > 0) {
            LOG.info("Notification retention purge: {} read/dismissed + {} unread removed",
                    readPurged, unreadPurged);
        }
    }
}
