package io.casehub.platform.notification;

import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.ReplyHint;
import io.casehub.platform.api.notification.ReplyHintResolver;

public class PassthroughReplyHintResolver implements ReplyHintResolver {
    @Override
    public ReplyHint resolve(NotificationInput input) {
        return input.replyHint();
    }
}
