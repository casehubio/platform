package io.casehub.platform.notification.dispatch;

import io.casehub.platform.api.delivery.DeliveryChannelDescriptor;
import io.casehub.platform.api.delivery.DeliveryChannelRegistry;
import io.casehub.platform.api.delivery.NotificationDeliverer;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.settings.ChannelPreference;
import io.casehub.platform.api.notification.settings.SuppressionResult;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
     * @return set of resolved channels with deliverer references and suppression flags
     */
    public Set<ResolvedChannel> route(final Map<String, ChannelPreference> channelDefaults,
                                      final SuppressionResult suppressionResult,
                                      final NotificationSeverity severity) {
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
            if (severity.ordinal() < minSeverity.ordinal()) {
                continue;
            }

            // Resolve deliverer
            final NotificationDeliverer deliverer = channelRegistry.resolveDeliverer(channelId)
                    .orElse(null);
            if (deliverer == null) {
                continue;
            }

            // Determine suppression: external channels suppressed during snooze or quiet hours
            final boolean suppressed = descriptor.external()
                    && (suppressionResult.isSnoozed() || suppressionResult.quietHoursActive());

            result.add(new ResolvedChannel(channelId, deliverer, suppressed));
        }

        return result;
    }
}
