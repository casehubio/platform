package io.casehub.platform.notification.dispatch;

import io.casehub.platform.api.delivery.DeliveryChannelDescriptor;
import io.casehub.platform.api.delivery.DeliveryChannels;
import io.casehub.platform.api.delivery.DeliveryResult;
import io.casehub.platform.api.delivery.DigestSchedule;
import io.casehub.platform.api.delivery.NotificationDeliverer;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.settings.ChannelPreference;
import io.casehub.platform.api.notification.settings.SuppressionResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelRouterTest {

    private InMemoryDeliveryChannelRegistry registry;
    private ChannelRouter router;

    private static final NotificationDeliverer IN_APP_DELIVERER = new StubDeliverer(DeliveryChannels.IN_APP);
    private static final NotificationDeliverer EMAIL_DELIVERER = new StubDeliverer(DeliveryChannels.EMAIL);
    private static final NotificationDeliverer SMS_DELIVERER = new StubDeliverer(DeliveryChannels.SMS);

    @BeforeEach
    void setUp() {
        registry = new InMemoryDeliveryChannelRegistry();

        // Register in-app: internal, default enabled, INFO
        registry.register(
                new DeliveryChannelDescriptor(DeliveryChannels.IN_APP, "In-App Inbox",
                        false, true, NotificationSeverity.INFO, null),
                IN_APP_DELIVERER);

        // Register email: external, default enabled, WARNING
        registry.register(
                new DeliveryChannelDescriptor(DeliveryChannels.EMAIL, "Email",
                        true, true, NotificationSeverity.WARNING, null),
                EMAIL_DELIVERER);

        // Register SMS: external, default disabled, URGENT
        registry.register(
                new DeliveryChannelDescriptor(DeliveryChannels.SMS, "SMS",
                        true, false, NotificationSeverity.URGENT, null),
                SMS_DELIVERER);

        router = new ChannelRouter(registry);
    }

    @Test
    void route_returnsInApp_whenNoPreferences() {
        // No user preferences — fall back to channel defaults
        // INFO severity: in-app (enabled, INFO threshold) should pass,
        // email (enabled, WARNING threshold) should fail severity check,
        // SMS (disabled) should be excluded
        var result = router.route(
                Map.of(),
                new SuppressionResult(false, false, false),
                NotificationSeverity.INFO);

        assertThat(result).hasSize(1);
        assertThat(result).extracting(ResolvedChannel::channelId)
                .containsExactly(DeliveryChannels.IN_APP);
    }

    @Test
    void route_respectsMinSeverity_suppressesBelowThreshold() {
        // WARNING severity: in-app (INFO) passes, email (WARNING) passes, SMS disabled
        var result = router.route(
                Map.of(),
                new SuppressionResult(false, false, false),
                NotificationSeverity.WARNING);

        assertThat(result).extracting(ResolvedChannel::channelId)
                .containsExactlyInAnyOrder(DeliveryChannels.IN_APP, DeliveryChannels.EMAIL);
    }

    @Test
    void route_respectsEnabledFlag() {
        // SMS is disabled by default — should not appear even with URGENT severity
        var result = router.route(
                Map.of(),
                new SuppressionResult(false, false, false),
                NotificationSeverity.URGENT);

        assertThat(result).extracting(ResolvedChannel::channelId)
                .containsExactlyInAnyOrder(DeliveryChannels.IN_APP, DeliveryChannels.EMAIL);
        assertThat(result).extracting(ResolvedChannel::channelId)
                .doesNotContain(DeliveryChannels.SMS);
    }

    @Test
    void route_marksSuppressed_whenSnoozedAndExternal() {
        var result = router.route(
                Map.of(),
                new SuppressionResult(false, true, false),
                NotificationSeverity.URGENT);

        // In-app should not be suppressed (internal)
        var inApp = result.stream()
                .filter(rc -> rc.channelId().equals(DeliveryChannels.IN_APP))
                .findFirst().orElseThrow();
        assertThat(inApp.suppressed()).isFalse();

        // Email should be suppressed (external + snoozed)
        var email = result.stream()
                .filter(rc -> rc.channelId().equals(DeliveryChannels.EMAIL))
                .findFirst().orElseThrow();
        assertThat(email.suppressed()).isTrue();
    }

    @Test
    void route_marksSuppressed_whenQuietHoursAndExternal() {
        var result = router.route(
                Map.of(),
                new SuppressionResult(false, false, true),
                NotificationSeverity.URGENT);

        var inApp = result.stream()
                .filter(rc -> rc.channelId().equals(DeliveryChannels.IN_APP))
                .findFirst().orElseThrow();
        assertThat(inApp.suppressed()).isFalse();

        var email = result.stream()
                .filter(rc -> rc.channelId().equals(DeliveryChannels.EMAIL))
                .findFirst().orElseThrow();
        assertThat(email.suppressed()).isTrue();
    }

    @Test
    void route_doesNotSuppressInApp_whenSnoozed() {
        var result = router.route(
                Map.of(),
                new SuppressionResult(false, true, true),
                NotificationSeverity.INFO);

        var inApp = result.stream()
                .filter(rc -> rc.channelId().equals(DeliveryChannels.IN_APP))
                .findFirst().orElseThrow();
        assertThat(inApp.suppressed()).isFalse();
    }

    @Test
    void route_usesChannelDefaults_whenNoUserPreference() {
        // No user preference for in-app — falls back to descriptor default (enabled, INFO)
        var result = router.route(
                Map.of(),
                new SuppressionResult(false, false, false),
                NotificationSeverity.INFO);

        assertThat(result).extracting(ResolvedChannel::channelId)
                .contains(DeliveryChannels.IN_APP);
    }

    @Test
    void route_usesUserPreference_overChannelDefault() {
        // User enables SMS (default disabled) with INFO threshold
        var userPrefs = Map.of(
                DeliveryChannels.SMS, new ChannelPreference(true, NotificationSeverity.INFO, null));

        var result = router.route(
                userPrefs,
                new SuppressionResult(false, false, false),
                NotificationSeverity.INFO);

        assertThat(result).extracting(ResolvedChannel::channelId)
                .contains(DeliveryChannels.SMS);
    }

    @Test
    void route_userDisablesChannel() {
        // User disables in-app
        var userPrefs = Map.of(
                DeliveryChannels.IN_APP, new ChannelPreference(false, NotificationSeverity.INFO, null));

        var result = router.route(
                userPrefs,
                new SuppressionResult(false, false, false),
                NotificationSeverity.INFO);

        assertThat(result).extracting(ResolvedChannel::channelId)
                .doesNotContain(DeliveryChannels.IN_APP);
    }

    @Test
    void route_externalChannelWithDigest_markedDigested() {
        // Register email with default digest schedule
        registry = new InMemoryDeliveryChannelRegistry();
        registry.register(
                new DeliveryChannelDescriptor(DeliveryChannels.EMAIL, "Email",
                        true, true, NotificationSeverity.INFO,
                        new DigestSchedule.Interval(Duration.ofHours(4))),
                EMAIL_DELIVERER);

        router = new ChannelRouter(registry);

        var result = router.route(Map.of(),
                new SuppressionResult(false, false, false),
                NotificationSeverity.INFO);

        var email = result.stream()
                .filter(rc -> rc.channelId().equals(DeliveryChannels.EMAIL))
                .findFirst().orElseThrow();
        assertThat(email.digested()).isTrue();
    }

    @Test
    void route_urgentSeverity_bypassesDigest() {
        registry = new InMemoryDeliveryChannelRegistry();
        registry.register(
                new DeliveryChannelDescriptor(DeliveryChannels.EMAIL, "Email",
                        true, true, NotificationSeverity.INFO,
                        new DigestSchedule.Interval(Duration.ofHours(4))),
                EMAIL_DELIVERER);

        router = new ChannelRouter(registry);

        var result = router.route(Map.of(),
                new SuppressionResult(false, false, false),
                NotificationSeverity.URGENT);

        var email = result.stream()
                .filter(rc -> rc.channelId().equals(DeliveryChannels.EMAIL))
                .findFirst().orElseThrow();
        assertThat(email.digested()).isFalse();
    }

    @Test
    void route_internalChannel_neverDigested() {
        registry = new InMemoryDeliveryChannelRegistry();
        registry.register(
                new DeliveryChannelDescriptor(DeliveryChannels.IN_APP, "In-App",
                        false, true, NotificationSeverity.INFO, null),
                IN_APP_DELIVERER);

        router = new ChannelRouter(registry);

        var result = router.route(Map.of(),
                new SuppressionResult(false, false, false),
                NotificationSeverity.INFO);

        var inApp = result.stream()
                .filter(rc -> rc.channelId().equals(DeliveryChannels.IN_APP))
                .findFirst().orElseThrow();
        assertThat(inApp.digested()).isFalse();
    }

    @Test
    void route_userPreferenceOverridesDefaultDigest() {
        registry = new InMemoryDeliveryChannelRegistry();
        registry.register(
                new DeliveryChannelDescriptor(DeliveryChannels.EMAIL, "Email",
                        true, true, NotificationSeverity.INFO, null),
                EMAIL_DELIVERER);

        router = new ChannelRouter(registry);

        // User enables digest on email
        var userPrefs = Map.of(
                DeliveryChannels.EMAIL, new ChannelPreference(true, NotificationSeverity.INFO,
                        new DigestSchedule.Interval(Duration.ofHours(2))));

        var result = router.route(userPrefs,
                new SuppressionResult(false, false, false),
                NotificationSeverity.INFO);

        var email = result.stream()
                .filter(rc -> rc.channelId().equals(DeliveryChannels.EMAIL))
                .findFirst().orElseThrow();
        assertThat(email.digested()).isTrue();
    }

    @Test
    void route_noDigestSchedule_notDigested() {
        var result = router.route(Map.of(),
                new SuppressionResult(false, false, false),
                NotificationSeverity.WARNING);

        var email = result.stream()
                .filter(rc -> rc.channelId().equals(DeliveryChannels.EMAIL))
                .findFirst().orElseThrow();
        assertThat(email.digested()).isFalse();
    }

    // --- helpers ---

    private static final class StubDeliverer implements NotificationDeliverer {
        private final String channel;

        StubDeliverer(String channel) {
            this.channel = channel;
        }

        @Override
        public String channelId() {
            return channel;
        }

        @Override
        public DeliveryResult deliver(NotificationInput notification) {
            return new DeliveryResult(true, null);
        }
    }
}
