package io.casehub.platform.notification.dispatch;

import io.casehub.platform.api.delivery.DeliveryChannelRegistry;
import io.casehub.platform.api.delivery.DeliveryAttempt;
import io.casehub.platform.api.delivery.DeliveryChannelDescriptor;
import io.casehub.platform.api.delivery.DigestBuffer;
import io.casehub.platform.api.delivery.DigestBufferKey;
import io.casehub.platform.api.delivery.DigestGroupBy;
import io.casehub.platform.api.delivery.DigestSchedule;
import io.casehub.platform.api.delivery.DigestSummary;
import io.casehub.platform.api.notification.settings.ChannelPreference;
import io.casehub.platform.api.notification.settings.NotificationPreferenceStore;
import io.casehub.platform.api.notification.settings.NotificationPreferences;
import io.casehub.platform.api.notification.settings.SuppressionStore;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DigestFlushScheduler {

    private static final Logger LOG = Logger.getLogger(DigestFlushScheduler.class);

    private final DigestBuffer digestBuffer;
    private final NotificationPreferenceStore preferenceStore;
    private final SuppressionStore suppressionStore;
    private final SuppressionEvaluator suppressionEvaluator;
    private final DeliveryChannelRegistry channelRegistry;
    private final DeliveryTracker deliveryTracker;
    private final ConcurrentHashMap<DigestBufferKey, Instant> lastFlushTimes = new ConcurrentHashMap<>();
    private final Set<DigestBufferKey> quietHoursDeferredKeys = ConcurrentHashMap.newKeySet();

    public DigestFlushScheduler(DigestBuffer digestBuffer,
                                NotificationPreferenceStore preferenceStore,
                                SuppressionStore suppressionStore,
                                SuppressionEvaluator suppressionEvaluator,
                                DeliveryChannelRegistry channelRegistry,
                                DeliveryTracker deliveryTracker) {
        this.digestBuffer = digestBuffer;
        this.preferenceStore = preferenceStore;
        this.suppressionStore = suppressionStore;
        this.suppressionEvaluator = suppressionEvaluator;
        this.channelRegistry = channelRegistry;
        this.deliveryTracker = deliveryTracker;
    }

    public void tick() {
        Instant now = Instant.now();
        for (DigestBufferKey key : digestBuffer.pendingKeys()) {
            try {
                processKey(key, now);
            } catch (Exception e) {
                LOG.warnf(e, "Digest flush failed for key %s", key);
            }
        }
    }

    void processKey(DigestBufferKey key, Instant now) {
        var prefs = preferenceStore.get(key.userId(), key.tenancyId());
        var channelPreference = prefs.map(NotificationPreferences::channelDefaults)
                .map(cd -> cd.get(key.channelId()));
        boolean enabled = channelPreference.map(ChannelPreference::enabled)
                .orElseGet(() -> channelRegistry.resolve(key.channelId())
                        .map(DeliveryChannelDescriptor::defaultEnabled).orElse(false));
        if (!enabled) {
            quietHoursDeferredKeys.remove(key);
            return;
        }
        DigestSchedule schedule = channelPreference.map(ChannelPreference::digestSchedule)
                .orElseGet(() -> channelRegistry.resolve(key.channelId())
                        .map(DeliveryChannelDescriptor::defaultDigestSchedule)
                        .orElse(null));

        if (schedule == null) {
            LOG.debugf("Orphan drain for key %s — schedule removed", key);
            quietHoursDeferredKeys.remove(key);
            flushKey(key, now, null);
            return;
        }

        var quietHours = prefs.map(NotificationPreferences::quietHours).orElse(null);
        if (quietHours != null) {
            var qhResult = suppressionEvaluator.evaluateUserLevel(Optional.empty(), quietHours, now);
            if (qhResult.quietHoursActive()) {
                quietHoursDeferredKeys.add(key);
                return;
            }
        }

        Instant oldest = digestBuffer.oldestPendingTimestamp(key).orElse(now);
        Instant lastFlush = lastFlushTimes.getOrDefault(key, Instant.EPOCH);
        boolean deferredFlush = quietHoursDeferredKeys.contains(key);
        if (!deferredFlush && !schedule.isFlushDue(oldest, lastFlush, now)) {
            return;
        }

        var activeSnooze = suppressionStore.activeSnooze(key.userId(), key.tenancyId());
        var suppression = suppressionEvaluator.evaluateUserLevel(activeSnooze, quietHours, now);
        if (suppression.isSnoozed()) {
            return;
        }

        quietHoursDeferredKeys.remove(key);
        DigestGroupBy groupBy = channelPreference.map(ChannelPreference::groupBy).orElse(null);
        flushKey(key, now, groupBy);
    }

    private void flushKey(DigestBufferKey key, Instant now, DigestGroupBy groupBy) {
        Instant periodStart = lastFlushTimes.getOrDefault(key,
                digestBuffer.oldestPendingTimestamp(key).orElse(now));

        var descriptor = channelRegistry.resolve(key.channelId()).orElse(null);
        var deliverer = channelRegistry.resolveDeliverer(key.channelId()).orElse(null);
        if (deliverer == null) {
            LOG.warnf("No deliverer for channel '%s' — keeping digest buffered", key.channelId());
            return;
        }

        var guaranteedMinSeverity = descriptor != null ? descriptor.guaranteedMinSeverity() : null;
        var prepared = digestBuffer.drain(key, items -> {
            if (items.isEmpty()) return null;
            var summary = new DigestSummary(key.userId(), key.tenancyId(), key.channelId(),
                    items, periodStart, now, groupBy);
            var attempt = deliveryTracker.preRecordDigest(key.channelId(), summary, guaranteedMinSeverity);
            return new PreparedDigest(summary, attempt);
        });
        if (prepared == null) return;
        var summary = prepared.summary();
        var preRecorded = prepared.attempt();

        try {
            var result = deliverer.deliverDigest(summary);
            if (result.success()) {
                LOG.infof("Digest flushed: user=%s, channel=%s, count=%d, period=%s→%s",
                        key.userId(), key.channelId(), summary.notifications().size(), periodStart, now);
                lastFlushTimes.put(key, now);
                deliveryTracker.confirmDigestSuccess(preRecorded);
            } else {
                LOG.warnf("Digest delivery failed: user=%s, channel=%s, reason=%s",
                        key.userId(), key.channelId(), result.failureReason());
                deliveryTracker.confirmDigestFailure(preRecorded, result.failureReason());
            }
        } catch (Exception e) {
            LOG.warnf(e, "Digest delivery error: user=%s, channel=%s",
                    key.userId(), key.channelId());
            deliveryTracker.confirmDigestFailure(preRecorded, e.getMessage());
        }
    }

    private record PreparedDigest(DigestSummary summary, DeliveryAttempt attempt) {}
}
