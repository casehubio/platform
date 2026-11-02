package io.casehub.platform.api.notification;

import java.util.Objects;

public record NotificationClicked(Notification notification) {
    public NotificationClicked {
        Objects.requireNonNull(notification, "notification");
    }
}
