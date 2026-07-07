package io.casehub.platform.notification.dispatch;

import io.casehub.platform.api.delivery.DeliveryChannelDescriptor;
import io.casehub.platform.api.delivery.DeliveryChannelRegistry;
import io.casehub.platform.api.delivery.DigestSchedule;
import io.casehub.platform.api.delivery.NotificationDeliverer;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.settings.ChannelPreference;
import io.casehub.platform.api.notification.settings.QuietHoursAction;
import io.casehub.platform.api.notification.settings.SuppressionResult;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Routes notifications to delivery channels based on user preferences,
 * severity thresholds, and suppression state.
 *
 * <p>Only injected dependency is the startup-populated {@link DeliveryChannelRegistry}
 * (ConcurrentHashMap, no I/O).
 *
 * <p>For each available channel:
 * <ul>
 *   <li>Check user preference (or fall back to channel descriptor defaults)</li>
 *   <li>Check severity meets minimum threshold</li>
 *   <li>Check if external AND (snoozed OR quiet hours active) → mark suppressed</li>
 * </ul>
 */
@ApplicationScoped
public class ChannelRouter {

    private static final Logger LOG = Logger.getLogger(ChannelRouter.class);

    private final DeliveryChannelRegistry channelRegistry;

    @Inject
    public ChannelRouter(final DeliveryChannelRegistry channelRegistry) {
        this.channelRegistry = channelRegistry;
    }

    /**
     * Route a notification to eligible channels.
     *
     * @param channelDefaults  user's per-channel preferences (keyed by channelId)
     * @param suppressionResult suppression evaluation result
     * @param severity         notification severity
     * @param quietHoursAction action to take during quiet hours (null = SUPPRESS)
     * @return set of resolved channels with deliverer references and suppression flags
     */
    public Set<ResolvedChannel> route(final Map<String, ChannelPreference> channelDefaults,
                                      final SuppressionResult suppressionResult,
                                      final NotificationSeverity severity,
                                      final QuietHoursAction quietHoursAction) {
        final Set<ResolvedChannel> result = new LinkedHashSet<>();

        for (final DeliveryChannelDescriptor descriptor : channelRegistry.discover()) {
            final String channelId = descriptor.channelId();

            // Determine effective preference: user preference takes priority over channel default
            final ChannelPreference userPref = channelDefaults.get(channelId);
            final boolean enabled;
            final NotificationSeverity minSeverity;

            if (userPref != null) {
                enabled = userPref.enabled();
                minSeverity = userPref.minSeverity();
            } else {
                enabled = descriptor.defaultEnabled();
                minSeverity = descriptor.defaultMinSeverity();
            }

            // Skip disabled channels
            if (!enabled) {
                continue;
            }

            // Skip if severity is below minimum threshold
            if (!severity.isAtLeast(minSeverity)) {
                continue;
            }

            // Resolve deliverer
            final NotificationDeliverer deliverer = channelRegistry.resolveDeliverer(channelId)
                    .orElse(null);
            if (deliverer == null) {
                continue;
            }

            // Determine effective digest schedule: user preference overrides channel default
            final DigestSchedule effectiveDigest;
            if (userPref != null && userPref.digestSchedule() != null) {
                effectiveDigest = userPref.digestSchedule();
            } else {
                effectiveDigest = descriptor.defaultDigestSchedule();
            }

            // Quiet hours buffering: if BUFFER_FOR_DIGEST and quiet hours active, route to digest
            final boolean quietHoursBuffering = suppressionResult.quietHoursActive()
                    && quietHoursAction == QuietHoursAction.BUFFER_FOR_DIGEST
                    && effectiveDigest != null;

            // Log warning if BUFFER_FOR_DIGEST requested but no digest schedule available
            if (suppressionResult.quietHoursActive()
                    && quietHoursAction == QuietHoursAction.BUFFER_FOR_DIGEST
                    && effectiveDigest == null) {
                LOG.warnf("BUFFER_FOR_DIGEST on channel %s but no digest schedule — notification suppressed",
                        channelId);
            }

            // Determine suppression: external channels suppressed during snooze OR
            // (quiet hours AND NOT buffering)
            final boolean suppressed = descriptor.external()
                    && (suppressionResult.isSnoozed()
                        || (suppressionResult.quietHoursActive() && !quietHoursBuffering));

            // Determine digested: external, has digest schedule, AND
            // (severity < URGENT OR quiet hours buffering)
            final boolean digested = descriptor.external()
                    && effectiveDigest != null
                    && (!severity.isAtLeast(NotificationSeverity.URGENT) || quietHoursBuffering);

            result.add(new ResolvedChannel(channelId, deliverer, suppressed, digested));
        }

        return result;
    }
}
