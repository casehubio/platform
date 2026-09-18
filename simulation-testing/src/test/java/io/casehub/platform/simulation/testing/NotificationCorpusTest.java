package io.casehub.platform.simulation.testing;

import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationCorpusTest {

    @Test
    void storeReturnsPreConfiguredSeedWithOutputMapper() {
        var seed = NotificationCorpus.store("tenant-1");
        assertThat(seed.qualifiedName()).isEqualTo("notification-store.store");
        assertThat(seed.keyExtractor()).isNotNull();
    }

    @Test
    void singleArgAddDerivesBothKeyAndOutput() {
        var seed = NotificationCorpus.store("hospital-a");
        seed.add(NotificationCorpus.input("Alert", "sla.breach", NotificationSeverity.URGENT));

        var record = seed.build().get(0);
        assertThat(record.key()).isEqualTo("sla.breach:URGENT");
        assertThat(record.output()).isNotNull();
        assertThat(record.output().title()).isEqualTo("Alert");
        assertThat(record.output().status()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(record.output().id()).isNotNull();
    }

    @Test
    void inputCreatesValidNotificationInput() {
        NotificationInput input = NotificationCorpus.input("Title", "category", NotificationSeverity.INFO);
        assertThat(input.title()).isEqualTo("Title");
        assertThat(input.category()).isEqualTo("category");
        assertThat(input.severity()).isEqualTo(NotificationSeverity.INFO);
        assertThat(input.userId()).isNotNull();
    }

    @Test
    void fromInputPreservesFields() {
        var input = NotificationCorpus.input("Alert", "test", NotificationSeverity.WARNING);
        var notification = NotificationCorpus.fromInput(input);
        assertThat(notification.title()).isEqualTo("Alert");
        assertThat(notification.category()).isEqualTo("test");
        assertThat(notification.severity()).isEqualTo(NotificationSeverity.WARNING);
        assertThat(notification.status()).isEqualTo(NotificationStatus.UNREAD);
    }
}
