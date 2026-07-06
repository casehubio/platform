package io.casehub.platform.notification.dispatch;

import io.casehub.platform.api.delivery.DeliveryChannelRegistry;
import io.casehub.platform.api.delivery.DigestBuffer;
import io.casehub.platform.api.delivery.DigestBufferKey;
import io.casehub.platform.api.delivery.DigestSchedule;
import io.casehub.platform.api.delivery.DigestSummary;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.settings.ChannelPreference;
import io.casehub.platform.api.notification.settings.NotificationPreferenceStore;
import io.casehub.platform.api.notification.settings.NotificationPreferences;
import io.casehub.platform.api.notification.settings.SuppressionStore;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodic scheduler that flushes pending digest buffers.
 *
 * <p>Each tick iterates over all pending buffer keys and, for each:
 * <ol>
 *   <li>Resolves the user's digest schedule (orphan drain if null)</li>
 *   <li>Checks whether the schedule's flush-due condition is met</li>
 *   <li>Evaluates user-level suppression (snooze / quiet hours)</li>
 *   <li>Drains the buffer and delivers the digest summary</li>
 * </ol>
 *
 * <p>Per-key error isolation: a failure processing one key does not block others.
 */
@ApplicationScoped
public class DigestFlushScheduler {

    private static final Logger LOG = Logger.getLogger(DigestFlushScheduler.class);

    private final DigestBuffer digestBuffer;
    private final NotificationPreferenceStore preferenceStore;
    private final SuppressionStore suppressionStore;
    private final SuppressionEvaluator suppressionEvaluator;
    private final DeliveryChannelRegistry channelRegistry;
    private final ConcurrentHashMap<DigestBufferKey, Instant> lastFlushTimes = new ConcurrentHashMap<>();

    @Inject
    public DigestFlushScheduler(DigestBuffer digestBuffer,
                                NotificationPreferenceStore preferenceStore,
                                SuppressionStore suppressionStore,
                                SuppressionEvaluator suppressionEvaluator,
                                DeliveryChannelRegistry channelRegistry) {
        this.digestBuffer = digestBuffer;
        this.preferenceStore = preferenceStore;
        this.suppressionStore = suppressionStore;
        this.suppressionEvaluator = suppressionEvaluator;
        this.channelRegistry = channelRegistry;
    }

    @Scheduled(every = "${casehub.notification.digest.tick-interval:1m}")
    void tick() {
        for (DigestBufferKey key : digestBuffer.pendingKeys()) {
            try {
                processKey(key);
            } catch (Exception e) {
                LOG.warnf(e, "Digest flush failed for key %s", key);
            }
        }
    }

    void processKey(DigestBufferKey key) {
        Instant now = Instant.now();

        // Look up user's digest schedule
        var prefs = preferenceStore.get(key.userId(), key.tenancyId());
        DigestSchedule schedule = prefs
                .map(NotificationPreferences::channelDefaults)
                .map(cd -> cd.get(key.channelId()))
                .map(ChannelPreference::digestSchedule)
                .orElse(null);

        if (schedule == null) {
            // Orphan: user disabled digest since buffering — flush immediately
            LOG.debugf("Orphan drain for key %s — schedule removed", key);
            flushKey(key, now);
            return;
        }

        // Check if flush is due
        Instant oldest = digestBuffer.oldestPendingTimestamp(key).orElse(now);
        Instant lastFlush = lastFlushTimes.getOrDefault(key, Instant.EPOCH);
        if (!schedule.isFlushDue(oldest, lastFlush, now)) {
            return;
        }

        // Check user-level suppression (snooze / quiet hours)
        var activeSnooze = suppressionStore.activeSnooze(key.userId(), key.tenancyId());
        var quietHours = prefs.map(NotificationPreferences::quietHours).orElse(null);
        var suppression = suppressionEvaluator.evaluateUserLevel(activeSnooze, quietHours);
        if (suppression.isSnoozed() || suppression.quietHoursActive()) {
            LOG.debugf("Digest flush deferred for %s — snoozed=%s, quietHours=%s",
                    key, suppression.isSnoozed(), suppression.quietHoursActive());
            return;
        }

        flushKey(key, now);
    }

    private void flushKey(DigestBufferKey key, Instant now) {
        Instant periodStart = lastFlushTimes.getOrDefault(key,
                digestBuffer.oldestPendingTimestamp(key).orElse(now));

        List<NotificationInput> items = digestBuffer.drain(key);
        if (items.isEmpty()) {
            LOG.debugf("Empty drain for key %s — items consumed between pendingKeys() and drain()", key);
            return;
        }

        var summary = new DigestSummary(
                key.userId(), key.tenancyId(), key.channelId(),
                items, periodStart, now);

        channelRegistry.resolveDeliverer(key.channelId())
                .ifPresentOrElse(
                        deliverer -> {
                            try {
                                var result = deliverer.deliverDigest(summary);
                                if (result.success()) {
                                    LOG.infof("Digest flushed: user=%s, channel=%s, count=%d, period=%s→%s",
                                            key.userId(), key.channelId(), items.size(), periodStart, now);
                                    lastFlushTimes.put(key, now);
                                } else {
                                    LOG.warnf("Digest delivery failed: user=%s, channel=%s, reason=%s",
                                            key.userId(), key.channelId(), result.failureReason());
                                }
                            } catch (Exception e) {
                                LOG.warnf(e, "Digest delivery error: user=%s, channel=%s",
                                        key.userId(), key.channelId());
                            }
                        },
                        () -> LOG.warnf("No deliverer for channel '%s' — digest items lost", key.channelId())
                );
    }
}
