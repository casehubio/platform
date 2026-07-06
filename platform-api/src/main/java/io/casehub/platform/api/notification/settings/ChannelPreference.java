package io.casehub.platform.api.notification.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.casehub.platform.api.delivery.DigestSchedule;
import io.casehub.platform.api.notification.NotificationSeverity;

import java.util.Objects;

/**
 * Per-channel notification preference.
 *
 * @param enabled         whether the channel is enabled
 * @param minSeverity     minimum severity for delivery (INFO < WARNING < URGENT)
 * @param digestSchedule  digest schedule (null = immediate delivery)
 */
public record ChannelPreference(
        boolean enabled,
        NotificationSeverity minSeverity,
        DigestSchedule digestSchedule
) {
    public ChannelPreference {
        Objects.requireNonNull(minSeverity, "minSeverity");
    }

    @JsonIgnore
    public boolean isDigested() {
        return digestSchedule != null;
    }
}
