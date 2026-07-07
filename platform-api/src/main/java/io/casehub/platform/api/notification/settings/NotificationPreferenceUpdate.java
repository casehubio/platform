package io.casehub.platform.api.notification.settings;

import java.util.Map;
import java.util.Objects;

/**
 * Update to user notification preferences. All fields are nullable — null means
 * "don't change."
 *
 * <p>{@link #clearQuietHours} is a separate flag because {@code quietHours=null}
 * means "don't change."
 *
 * @param channelDefaults per-channel preferences keyed by channelId; null = don't change
 * @param quietHours      quiet hours configuration; null = don't change
 * @param clearQuietHours if true, remove quiet hours (since quietHours=null means "don't change")
 */
public record NotificationPreferenceUpdate(
        Map<String, ChannelPreference> channelDefaults,
        QuietHours quietHours,
        boolean clearQuietHours
) {
    public NotificationPreferenceUpdate {
        if (channelDefaults != null) {
            channelDefaults = Map.copyOf(channelDefaults);
        }
    }
}
