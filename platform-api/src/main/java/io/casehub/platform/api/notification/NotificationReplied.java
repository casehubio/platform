package io.casehub.platform.api.notification;

import java.util.Objects;

public record NotificationReplied(Notification notification, String value) {
    public NotificationReplied {
        Objects.requireNonNull(notification, "notification");
    }
}
