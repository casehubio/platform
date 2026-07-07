package io.casehub.platform.api.delivery;

import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DigestSummaryTest {

    private static final NotificationInput SAMPLE = new NotificationInput(
            "user-1", "tenant-1", "Title", null, "test",
            NotificationSeverity.INFO, null,
            new NotificationSource(UUID.randomUUID().toString(), "work-item", "wi-1", "actor-1"));

    @Test
    void rejectsEmptyNotifications() {
        assertThatThrownBy(() -> new DigestSummary(
                "user-1", "tenant-1", "email", List.of(),
                Instant.now(), Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("notifications must not be empty");
    }

    @Test
    void rejectsNullNotifications() {
        assertThatThrownBy(() -> new DigestSummary(
                "user-1", "tenant-1", "email", null,
                Instant.now(), Instant.now(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void createsDefensiveCopy() {
        var mutable = new java.util.ArrayList<>(List.of(SAMPLE));
        var summary = new DigestSummary("user-1", "tenant-1", "email",
                mutable, Instant.now(), Instant.now(), null);
        mutable.add(SAMPLE);
        assertThat(summary.notifications()).hasSize(1);
    }
}
