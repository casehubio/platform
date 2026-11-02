package io.casehub.platform.notification;

import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationSource;
import io.casehub.platform.api.notification.ReplyHint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PassthroughReplyHintResolverTest {

    private final PassthroughReplyHintResolver resolver = new PassthroughReplyHintResolver();

    @Test
    void returnsInputHintWhenPresent() {
        var hint = ReplyHint.acknowledge();
        var input = new NotificationInput("user-1", "t1", "title", null, "cat",
                NotificationSeverity.INFO, null,
                new NotificationSource("e1", "type", "id1", "actor1"), hint);
        assertThat(resolver.resolve(input)).isEqualTo(hint);
    }

    @Test
    void returnsNullWhenInputHintNull() {
        var input = new NotificationInput("user-1", "t1", "title", null, "cat",
                NotificationSeverity.INFO, null,
                new NotificationSource("e1", "type", "id1", "actor1"), null);
        assertThat(resolver.resolve(input)).isNull();
    }
}
