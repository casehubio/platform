package io.casehub.platform.simulation.testing;

import io.casehub.platform.api.notification.Notification;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationSource;
import io.casehub.platform.api.notification.NotificationStatus;
import io.casehub.platform.api.util.UUIDv7;
import io.casehub.platform.simulation.CorpusSeed;
import io.casehub.platform.simulation.generated.NotificationStoreQN;

import java.time.Instant;

public final class NotificationCorpus {

    private NotificationCorpus() {}

    public static CorpusSeed<NotificationInput, Notification> store(String tenancyId) {
        return new CorpusSeed<NotificationInput, Notification>(NotificationStoreQN.STORE, tenancyId)
                .withKeyExtractor(i -> i.category() + ":" + i.severity())
                .withOutputMapper(NotificationCorpus::fromInput);
    }

    public static NotificationInput input(String title, String category,
                                            NotificationSeverity severity) {
        return new NotificationInput("user-1", "default", title, null, category,
                severity, null, new NotificationSource("evt-1", "system", "sys-1", "actor-1"));
    }

    public static Notification fromInput(NotificationInput input) {
        return new Notification(UUIDv7.generate().toString(), input.userId(),
                input.tenancyId(), input.title(), input.body(), input.category(),
                input.severity(), input.actionUrl(), input.source(),
                NotificationStatus.UNREAD, Instant.now(), null, null);
    }
}
