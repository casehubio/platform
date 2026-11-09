package io.casehub.platform.notification.dispatch;

import io.casehub.platform.api.preferences.IntPreference;
import io.casehub.platform.api.preferences.PlatformPreferenceKeys;
import io.casehub.platform.delivery.channel.inmem.InMemoryDeliveryChannelRegistry;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.delivery.DeliveryAttempt;
import io.casehub.platform.api.delivery.DeliveryChannelDescriptor;
import io.casehub.platform.api.delivery.DeliveryChannels;
import io.casehub.platform.api.delivery.DeliveryResult;
import io.casehub.platform.api.delivery.DigestBufferKey;
import io.casehub.platform.api.delivery.DigestGroupBy;
import io.casehub.platform.api.delivery.DigestSchedule;
import io.casehub.platform.api.delivery.DigestSummary;
import io.casehub.platform.api.delivery.NotificationDeliverer;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationSource;
import io.casehub.platform.api.notification.settings.ChannelPreference;
import io.casehub.platform.api.notification.settings.MuteRule;
import io.casehub.platform.api.notification.settings.MuteRuleInput;
import io.casehub.platform.api.notification.settings.NotificationPreferenceStore;
import io.casehub.platform.api.notification.settings.NotificationPreferenceUpdate;
import io.casehub.platform.api.notification.settings.NotificationPreferences;
import io.casehub.platform.api.notification.settings.QuietHours;
import io.casehub.platform.api.notification.settings.QuietHoursAction;
import io.casehub.platform.api.notification.settings.Snooze;
import io.casehub.platform.api.notification.settings.SnoozeInput;
import io.casehub.platform.api.notification.settings.SuppressionStore;
import io.casehub.platform.delivery.digest.inmem.InMemoryDigestBuffer;
import io.casehub.platform.delivery.tracking.inmem.InMemoryDeliveryAttemptStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DigestFlushSchedulerTest {

    private InMemoryDigestBuffer buffer;
    private StubPreferenceStore preferenceStore;
    private StubSuppressionStore suppressionStore;
    private InMemoryDeliveryChannelRegistry channelRegistry;
    private CapturingDigestDeliverer emailDeliverer;
    private DigestFlushScheduler scheduler;

    private static final String USER = "user-1";
    private static final String TENANT = "tenant-1";
    private static final DigestBufferKey EMAIL_KEY = new DigestBufferKey(USER, TENANT, DeliveryChannels.EMAIL);

    @BeforeEach
    void setUp() {
        buffer = new InMemoryDigestBuffer(500,
                EngagementRecorderTest.providerWith(
                        PlatformPreferenceKeys.DIGEST_RETENTION_DAYS, IntPreference.of(90)));
        preferenceStore = new StubPreferenceStore();
        suppressionStore = new StubSuppressionStore();
        channelRegistry = new InMemoryDeliveryChannelRegistry();
        emailDeliverer = new CapturingDigestDeliverer(DeliveryChannels.EMAIL);

        channelRegistry.register(
                new DeliveryChannelDescriptor(DeliveryChannels.EMAIL, "Email",
                        true, true, NotificationSeverity.INFO,
                        new DigestSchedule.Interval(Duration.ofHours(4)), null, null),
                emailDeliverer);

        preferenceStore.prefs = new NotificationPreferences(USER, TENANT,
                Map.of(DeliveryChannels.EMAIL, new ChannelPreference(true, NotificationSeverity.INFO,
                        new DigestSchedule.Interval(Duration.ofHours(4)), null)),
                null, Instant.now());

        var deliveryAttemptStore = new io.casehub.platform.delivery.tracking.inmem.InMemoryDeliveryAttemptStore(10000);
        var objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        var deliveryTracker = new DeliveryTracker(deliveryAttemptStore, objectMapper, Duration.ofSeconds(30));

        scheduler = new DigestFlushScheduler(
                buffer, preferenceStore, suppressionStore,
                new SuppressionEvaluator(), channelRegistry, deliveryTracker);
    }

    @Test
    void tick_doesNotFlush_whenIntervalNotElapsed() {
        // Buffer an item — timestamp will be ~now
        buffer.add(EMAIL_KEY, sampleInput("Notification 1"));

        // Use a 1-minute interval; item was just added so NOT due yet
        // (oldest + 1min is in the future, so isFlushDue returns false)
        preferenceStore.prefs = new NotificationPreferences(USER, TENANT,
                Map.of(DeliveryChannels.EMAIL, new ChannelPreference(true, NotificationSeverity.INFO,
                        new DigestSchedule.Interval(Duration.ofMinutes(1)), null)),
                null, Instant.now());

        scheduler.tick();
        assertThat(emailDeliverer.received).isEmpty();
    }

    static Stream<NotificationPreferences> preferencesWithoutDigestSchedule() {
        return Stream.of(null,
                new NotificationPreferences(USER, TENANT, Map.of(), null, Instant.now()),
                new NotificationPreferences(USER, TENANT,
                        Map.of(DeliveryChannels.EMAIL,
                                new ChannelPreference(true, NotificationSeverity.INFO, null, null)),
                        null, Instant.now()));
    }

    @ParameterizedTest
    @MethodSource("preferencesWithoutDigestSchedule")
    void processKey_usesChannelDefaultWhenUserScheduleAbsent(NotificationPreferences preferences) {
        preferenceStore.prefs = preferences;
        buffer.add(EMAIL_KEY, sampleInput("Wait for default schedule"));
        Instant oldest = buffer.oldestPendingTimestamp(EMAIL_KEY).orElseThrow();

        scheduler.processKey(EMAIL_KEY, oldest.plus(1, ChronoUnit.MINUTES));
        assertThat(emailDeliverer.received).isEmpty();
        assertThat(buffer.pendingCount(EMAIL_KEY)).isEqualTo(1);

        scheduler.processKey(EMAIL_KEY, oldest.plus(4, ChronoUnit.HOURS));
        assertThat(emailDeliverer.received).hasSize(1);
        assertThat(buffer.pendingCount(EMAIL_KEY)).isZero();
    }

    @Test
    void processKey_userScheduleOverridesChannelDefault() {
        preferenceStore.prefs = new NotificationPreferences(USER, TENANT,
                Map.of(DeliveryChannels.EMAIL, new ChannelPreference(true, NotificationSeverity.INFO,
                        new DigestSchedule.Interval(Duration.ofMinutes(1)), null)), null, Instant.now());
        buffer.add(EMAIL_KEY, sampleInput("Use user schedule"));
        Instant oldest = buffer.oldestPendingTimestamp(EMAIL_KEY).orElseThrow();

        scheduler.processKey(EMAIL_KEY, oldest.plus(1, ChronoUnit.MINUTES));
        assertThat(emailDeliverer.received).hasSize(1);
    }

    @Test
    void tick_defersWhenSnoozed() {
        buffer.add(EMAIL_KEY, sampleInput("Notification 1"));
        suppressionStore.snooze = new Snooze(USER, TENANT,
                Instant.now().plus(1, ChronoUnit.HOURS), Instant.now());

        // Even with a very short interval, snooze defers
        preferenceStore.prefs = new NotificationPreferences(USER, TENANT,
                Map.of(DeliveryChannels.EMAIL, new ChannelPreference(true, NotificationSeverity.INFO,
                        new DigestSchedule.Interval(Duration.ofMinutes(1)), null)),
                null, Instant.now());

        scheduler.tick();
        assertThat(emailDeliverer.received).isEmpty();
        assertThat(buffer.pendingKeys()).contains(EMAIL_KEY);
    }

    @Test
    void tick_perKeyErrorIsolation() {
        var key2 = new DigestBufferKey("user-2", TENANT, DeliveryChannels.EMAIL);
        buffer.add(EMAIL_KEY, sampleInput("Item for user-1"));
        buffer.add(key2, sampleInput("Item for user-2"));

        // user-1 preference store throws
        preferenceStore.throwForUser = USER;
        preferenceStore.prefs2 = new NotificationPreferences("user-2", TENANT,
                Map.of(DeliveryChannels.EMAIL, new ChannelPreference(true, NotificationSeverity.INFO,
                        new DigestSchedule.Interval(Duration.ofMinutes(1)), null)),
                null, Instant.now());

        // user-1 fails but user-2 should still be processed (though not due yet)
        scheduler.tick();
        // No crash — error isolated
        assertThat(buffer.pendingKeys()).contains(EMAIL_KEY);
    }

    @Test
    void tick_drainsOrphansWhenScheduleRemoved() {
        buffer.add(EMAIL_KEY, sampleInput("Orphaned item"));
        // Neither the user nor the channel has a digest schedule anymore.
        channelRegistry.register(new DeliveryChannelDescriptor(DeliveryChannels.EMAIL, "Email",
                true, true, NotificationSeverity.INFO, null, null, null), emailDeliverer);
        preferenceStore.prefs = new NotificationPreferences(USER, TENANT,
                Map.of(DeliveryChannels.EMAIL, new ChannelPreference(true, NotificationSeverity.INFO, null, null)),
                null, Instant.now());

        scheduler.tick();
        // Orphan should be flushed immediately
        assertThat(emailDeliverer.received).hasSize(1);
        assertThat(buffer.pendingKeys()).doesNotContain(EMAIL_KEY);
    }

    @Test
    void processKey_flushesWhenIntervalElapsed() {
        buffer.add(EMAIL_KEY, sampleInput("Notification 1"));
        preferenceStore.prefs = new NotificationPreferences(USER, TENANT,
                Map.of(DeliveryChannels.EMAIL, new ChannelPreference(true, NotificationSeverity.INFO,
                        new DigestSchedule.Interval(Duration.ofHours(4)), null)),
                null, Instant.now());

        // Simulate 5 hours after buffer add
        Instant futureNow = Instant.now().plus(5, ChronoUnit.HOURS);
        scheduler.processKey(EMAIL_KEY, futureNow);

        assertThat(emailDeliverer.received).hasSize(1);
        assertThat(emailDeliverer.received.get(0).notifications()).hasSize(1);
    }

    @Test
    void processKey_defersWhenQuietHoursActive() {
        buffer.add(EMAIL_KEY, sampleInput("Notification 1"));

        // Quiet hours 22:00-07:00 UTC, now is 23:00 UTC
        var qh = new QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0), ZoneId.of("UTC"), null);
        preferenceStore.prefs = new NotificationPreferences(USER, TENANT,
                Map.of(DeliveryChannels.EMAIL, new ChannelPreference(true, NotificationSeverity.INFO,
                        new DigestSchedule.Interval(Duration.ofMinutes(1)), null)),
                qh, Instant.now());

        Instant duringQH = LocalDate.of(2026, 1, 1).atTime(23, 0).atZone(ZoneId.of("UTC")).toInstant();
        scheduler.processKey(EMAIL_KEY, duringQH);

        assertThat(emailDeliverer.received).isEmpty();
        assertThat(buffer.pendingKeys()).contains(EMAIL_KEY);
    }

    @Test
    void processKey_flushesImmediatelyAfterQuietHoursEnd() {
        buffer.add(EMAIL_KEY, sampleInput("Deferred notification"));

        var qh = new QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0), ZoneId.of("UTC"),
                QuietHoursAction.BUFFER_FOR_DIGEST);
        preferenceStore.prefs = new NotificationPreferences(USER, TENANT,
                Map.of(DeliveryChannels.EMAIL, new ChannelPreference(true, NotificationSeverity.INFO,
                        new DigestSchedule.Interval(Duration.ofHours(4)), null)),
                qh, Instant.now());

        // First tick during quiet hours — deferred
        Instant duringQH = LocalDate.of(2026, 1, 1).atTime(23, 0).atZone(ZoneId.of("UTC")).toInstant();
        scheduler.processKey(EMAIL_KEY, duringQH);
        assertThat(emailDeliverer.received).isEmpty();

        // Second tick after quiet hours end — flush immediately regardless of schedule
        Instant afterQH = LocalDate.of(2026, 1, 2).atTime(8, 0).atZone(ZoneId.of("UTC")).toInstant();
        scheduler.processKey(EMAIL_KEY, afterQH);
        assertThat(emailDeliverer.received).hasSize(1);
    }

    @Test
    void processKey_orphanDrainClearsQuietHoursDeferredKey() {
        buffer.add(EMAIL_KEY, sampleInput("Deferred notification"));

        var qh = new QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0), ZoneId.of("UTC"),
                QuietHoursAction.BUFFER_FOR_DIGEST);
        preferenceStore.prefs = new NotificationPreferences(USER, TENANT,
                Map.of(DeliveryChannels.EMAIL, new ChannelPreference(true, NotificationSeverity.INFO,
                        new DigestSchedule.Interval(Duration.ofHours(4)), null)),
                qh, Instant.now());

        // Step 1: tick during quiet hours — key deferred
        Instant duringQH = LocalDate.of(2026, 1, 1).atTime(23, 0).atZone(ZoneId.of("UTC")).toInstant();
        scheduler.processKey(EMAIL_KEY, duringQH);
        assertThat(emailDeliverer.received).isEmpty();

        // Step 2: user and channel schedules are removed while still in quiet hours.
        channelRegistry.register(new DeliveryChannelDescriptor(DeliveryChannels.EMAIL, "Email",
                true, true, NotificationSeverity.INFO, null, null, null), emailDeliverer);
        preferenceStore.prefs = new NotificationPreferences(USER, TENANT,
                Map.of(DeliveryChannels.EMAIL, new ChannelPreference(true, NotificationSeverity.INFO, null, null)),
                qh, Instant.now());

        // Step 3: next tick — orphan drain flushes buffer
        Instant stillDuringQH = LocalDate.of(2026, 1, 1).atTime(23, 30).atZone(ZoneId.of("UTC")).toInstant();
        scheduler.processKey(EMAIL_KEY, stillDuringQH);
        assertThat(emailDeliverer.received).hasSize(1);
        emailDeliverer.received.clear();

        // Step 4: user re-enables digest schedule, new notification arrives
        preferenceStore.prefs = new NotificationPreferences(USER, TENANT,
                Map.of(DeliveryChannels.EMAIL, new ChannelPreference(true, NotificationSeverity.INFO,
                        new DigestSchedule.Interval(Duration.ofHours(4)), null)),
                null, Instant.now());
        buffer.add(EMAIL_KEY, sampleInput("New notification"));

        // Step 5: tick outside quiet hours — should follow normal schedule, NOT phantom deferred flush
        Instant afterQH = LocalDate.of(2026, 1, 2).atTime(8, 0).atZone(ZoneId.of("UTC")).toInstant();
        scheduler.processKey(EMAIL_KEY, afterQH);
        assertThat(emailDeliverer.received).as("phantom deferred key should not bypass schedule gate").isEmpty();
    }

    @Test
    void processKey_passesGroupByToDigestSummary() {
        buffer.add(EMAIL_KEY, sampleInput("Notification 1"));
        preferenceStore.prefs = new NotificationPreferences(USER, TENANT,
                Map.of(DeliveryChannels.EMAIL, new ChannelPreference(true, NotificationSeverity.INFO,
                        new DigestSchedule.Interval(Duration.ofHours(4)), DigestGroupBy.CATEGORY)),
                null, Instant.now());

        Instant futureNow = Instant.now().plus(5, ChronoUnit.HOURS);
        scheduler.processKey(EMAIL_KEY, futureNow);

        assertThat(emailDeliverer.received).hasSize(1);
        assertThat(emailDeliverer.received.get(0).groupBy()).isEqualTo(DigestGroupBy.CATEGORY);
    }

    @Test
    void trackingFailureKeepsDigestBufferedAndDoesNotSend() {
        buffer.add(EMAIL_KEY, sampleInput("Keep me"));
        var failingStore = new InMemoryDeliveryAttemptStore() {
            @Override
            public void store(DeliveryAttempt attempt) {
                throw new IllegalStateException("Tracking unavailable");
            }
        };
        var mapper = new ObjectMapper().findAndRegisterModules();
        var failingScheduler = new DigestFlushScheduler(buffer, preferenceStore, suppressionStore,
                new SuppressionEvaluator(), channelRegistry,
                new DeliveryTracker(failingStore, mapper, Duration.ofSeconds(30)));

        assertThatThrownBy(() ->
                failingScheduler.processKey(EMAIL_KEY, Instant.now().plus(5, ChronoUnit.HOURS)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Tracking unavailable");
        assertThat(buffer.pendingCount(EMAIL_KEY)).isEqualTo(1);
        assertThat(emailDeliverer.received).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void disabledChannelRetainsDigestUntilReenabled(boolean hasSchedule) {
        buffer.add(EMAIL_KEY, sampleInput("Queued while enabled"));
        var schedule = hasSchedule ? new DigestSchedule.Interval(Duration.ofHours(4)) : null;
        preferenceStore.prefs = new NotificationPreferences(USER, TENANT,
                Map.of(DeliveryChannels.EMAIL, new ChannelPreference(false, NotificationSeverity.INFO,
                        schedule, null)), null, Instant.now());
        Instant due = buffer.oldestPendingTimestamp(EMAIL_KEY).orElseThrow().plus(5, ChronoUnit.HOURS);

        scheduler.processKey(EMAIL_KEY, due);
        assertThat(emailDeliverer.received).isEmpty();
        assertThat(buffer.pendingCount(EMAIL_KEY)).isEqualTo(1);

        preferenceStore.prefs = new NotificationPreferences(USER, TENANT,
                Map.of(DeliveryChannels.EMAIL, new ChannelPreference(true, NotificationSeverity.INFO,
                        schedule, null)), null, Instant.now());
        scheduler.processKey(EMAIL_KEY, due);
        assertThat(emailDeliverer.received).hasSize(1);
        assertThat(buffer.pendingCount(EMAIL_KEY)).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void channelDisabledByDefaultRetainsDigestWithoutUserOverride(boolean hasPreferences) {
        channelRegistry.register(new DeliveryChannelDescriptor(DeliveryChannels.EMAIL, "Email",
                true, false, NotificationSeverity.INFO, null, null, null), emailDeliverer);
        preferenceStore.prefs = hasPreferences
                ? new NotificationPreferences(USER, TENANT, Map.of(), null, Instant.now()) : null;
        buffer.add(EMAIL_KEY, sampleInput("Channel now disabled"));

        scheduler.processKey(EMAIL_KEY, Instant.now().plus(5, ChronoUnit.HOURS));
        assertThat(emailDeliverer.received).isEmpty();
        assertThat(buffer.pendingCount(EMAIL_KEY)).isEqualTo(1);
    }

    @Test
    void explicitUserEnablementOverridesDisabledChannelDefault() {
        channelRegistry.register(new DeliveryChannelDescriptor(DeliveryChannels.EMAIL, "Email",
                true, false, NotificationSeverity.INFO, null, null, null), emailDeliverer);
        buffer.add(EMAIL_KEY, sampleInput("User enabled channel"));

        scheduler.processKey(EMAIL_KEY, Instant.now().plus(5, ChronoUnit.HOURS));
        assertThat(emailDeliverer.received).hasSize(1);
    }

    @Test
    void disablingChannelClearsQuietHoursDeferral() {
        buffer.add(EMAIL_KEY, sampleInput("Deferred before disabling"));
        var schedule = new DigestSchedule.Interval(Duration.ofHours(4));
        var quietHours = new QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0), ZoneId.of("UTC"),
                QuietHoursAction.BUFFER_FOR_DIGEST);
        Instant duringQuietHours = LocalDate.of(2026, 1, 1).atTime(23, 0).atZone(ZoneId.of("UTC")).toInstant();
        preferenceStore.prefs = new NotificationPreferences(USER, TENANT,
                Map.of(DeliveryChannels.EMAIL, new ChannelPreference(true, NotificationSeverity.INFO,
                        schedule, null)), quietHours, Instant.now());
        scheduler.processKey(EMAIL_KEY, duringQuietHours);

        preferenceStore.prefs = new NotificationPreferences(USER, TENANT,
                Map.of(DeliveryChannels.EMAIL, new ChannelPreference(false, NotificationSeverity.INFO,
                        schedule, null)), quietHours, Instant.now());
        scheduler.processKey(EMAIL_KEY, duringQuietHours);

        preferenceStore.prefs = new NotificationPreferences(USER, TENANT,
                Map.of(DeliveryChannels.EMAIL, new ChannelPreference(true, NotificationSeverity.INFO,
                        schedule, null)), null, Instant.now());
        scheduler.processKey(EMAIL_KEY, buffer.oldestPendingTimestamp(EMAIL_KEY).orElseThrow()
                .plus(1, ChronoUnit.MINUTES));
        assertThat(emailDeliverer.received).isEmpty();
        assertThat(buffer.pendingCount(EMAIL_KEY)).isEqualTo(1);
    }

    // --- helpers ---

    private static NotificationInput sampleInput(String title) {
        return new NotificationInput(USER, TENANT, title, null, "test",
                NotificationSeverity.INFO, null,
                new NotificationSource(UUID.randomUUID().toString(), "work-item", "wi-1", "actor-1"), null);
    }

    private static final class CapturingDigestDeliverer implements NotificationDeliverer {
        private final String channel;
        final List<DigestSummary> received = new ArrayList<>();

        CapturingDigestDeliverer(String channel) { this.channel = channel; }

        @Override
        public String channelId() { return channel; }

        @Override
        public DeliveryResult deliver(NotificationInput notification) {
            return new DeliveryResult(true, null);
        }

        @Override
        public DeliveryResult deliverDigest(DigestSummary summary) {
            received.add(summary);
            return new DeliveryResult(true, null);
        }
    }

    private static final class StubPreferenceStore implements NotificationPreferenceStore {
        NotificationPreferences prefs;
        NotificationPreferences prefs2;
        String throwForUser;

        @Override
        public Optional<NotificationPreferences> get(String userId, String tenancyId) {
            if (userId.equals(throwForUser)) throw new RuntimeException("Simulated failure");
            if (prefs2 != null && userId.equals(prefs2.userId())) return Optional.of(prefs2);
            return prefs != null && userId.equals(prefs.userId()) ? Optional.of(prefs) : Optional.empty();
        }

        @Override
        public NotificationPreferences update(String u, String t, NotificationPreferenceUpdate up) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubSuppressionStore implements SuppressionStore {
        Snooze snooze;

        @Override public MuteRule addMute(MuteRuleInput i) { throw new UnsupportedOperationException(); }
        @Override public List<MuteRule> activeMutes(String u, String t) { return List.of(); }
        @Override public boolean removeMute(String m, String u, String t) { throw new UnsupportedOperationException(); }
        @Override public Snooze activateSnooze(SnoozeInput i) { throw new UnsupportedOperationException(); }
        @Override public Optional<Snooze> activeSnooze(String u, String t) {
            return snooze != null && u.equals(snooze.userId()) ? Optional.of(snooze) : Optional.empty();
        }
        @Override public boolean cancelSnooze(String u, String t) { throw new UnsupportedOperationException(); }
    }
}
