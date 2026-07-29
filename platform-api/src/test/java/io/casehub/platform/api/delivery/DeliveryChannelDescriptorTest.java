package io.casehub.platform.api.delivery;

import io.casehub.platform.api.notification.NotificationSeverity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryChannelDescriptorTest {

    @Test
    void acceptsNullGuaranteedMinSeverity() {
        var desc = new DeliveryChannelDescriptor(
                "in_app", "In-App", false, true,
                NotificationSeverity.INFO, null, null, null);
        assertThat(desc.guaranteedMinSeverity()).isNull();
    }

    @Test
    void carriesGuaranteedMinSeverity() {
        var desc = new DeliveryChannelDescriptor(
                "email", "Email", true, true,
                NotificationSeverity.INFO, null, NotificationSeverity.WARNING, null);
        assertThat(desc.guaranteedMinSeverity()).isEqualTo(NotificationSeverity.WARNING);
    }

    @Test
    void defaultDestinationScopeIsPerUser() {
        var desc = new DeliveryChannelDescriptor(
                "test", "Test", true, false,
                NotificationSeverity.WARNING, null, null, null);
        assertThat(desc.destinationScope()).isEqualTo(DestinationScope.PER_USER);
    }

    @Test
    void carriesDestinationScope() {
        var desc = new DeliveryChannelDescriptor(
                "test", "Test", true, false,
                NotificationSeverity.WARNING, null, null, DestinationScope.PER_TENANT);
        assertThat(desc.destinationScope()).isEqualTo(DestinationScope.PER_TENANT);
    }
}
