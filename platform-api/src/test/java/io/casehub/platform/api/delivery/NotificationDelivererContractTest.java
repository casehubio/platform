package io.casehub.platform.api.delivery;

import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDelivererContractTest {

    @Test
    void deliverDigest_defaultMethod_collapsesToSingleNotification() {
        AtomicReference<NotificationInput> captured = new AtomicReference<>();

        NotificationDeliverer deliverer = new NotificationDeliverer() {
            @Override
            public String channelId() { return "test"; }

            @Override
            public DeliveryResult deliver(NotificationInput notification) {
                captured.set(notification);
                return new DeliveryResult(true, null);
            }
        };

        var input1 = sampleInput("Title 1");
        var input2 = sampleInput("Title 2");
        var input3 = sampleInput("Title 3");
        var summary = new DigestSummary("user-1", "tenant-1", "test",
                List.of(input1, input2, input3), Instant.now().minusSeconds(3600), Instant.now());

        DeliveryResult result = deliverer.deliverDigest(summary);

        assertThat(result.success()).isTrue();
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().title()).isEqualTo("3 new notifications");
        assertThat(captured.get().category()).isEqualTo("digest");
        assertThat(captured.get().userId()).isEqualTo("user-1");
        assertThat(captured.get().tenancyId()).isEqualTo("tenant-1");
    }

    private static NotificationInput sampleInput(String title) {
        return new NotificationInput("user-1", "tenant-1", title, null, "test",
                NotificationSeverity.INFO, null,
                new NotificationSource(UUID.randomUUID().toString(), "work-item", "wi-1", "actor-1"));
    }
}
