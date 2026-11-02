package io.casehub.platform.api.notification;

public interface ReplyHintResolver {
    ReplyHint resolve(NotificationInput input);
}
