package io.casehub.platform.api.notification.settings;

import io.casehub.platform.api.notification.NotificationSeverity;

import java.util.Objects;

/**
 * Per-channel notification preference.
 *
 * @param enabled     whether the channel is enabled
 * @param minSeverity minimum severity for delivery (INFO < WARNING < URGENT)
 */
public record ChannelPreference(
        boolean enabled,
        NotificationSeverity minSeverity
) {
    public ChannelPreference {
        Objects.requireNonNull(minSeverity, "minSeverity");
    }
}
