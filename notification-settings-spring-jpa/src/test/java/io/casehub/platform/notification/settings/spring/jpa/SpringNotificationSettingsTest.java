package io.casehub.platform.notification.settings.spring.jpa;

import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.settings.ChannelPreference;
import io.casehub.platform.api.notification.settings.MuteRule;
import io.casehub.platform.api.notification.settings.MuteRuleInput;
import io.casehub.platform.api.notification.settings.MuteScope;
import io.casehub.platform.api.notification.settings.NotificationPreferenceUpdate;
import io.casehub.platform.api.notification.settings.NotificationPreferences;
import io.casehub.platform.api.notification.settings.Snooze;
import io.casehub.platform.api.notification.settings.SnoozeInput;
import io.casehub.platform.notification.settings.jpa.MuteRuleEntity;
import io.casehub.platform.notification.settings.jpa.NotificationPreferencesEntity;
import io.casehub.platform.notification.settings.jpa.SnoozeEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(SpringNotificationSettingsTest.TestConfig.class)
class SpringNotificationSettingsTest {

    private static final String USER = "user-1";
    private static final String TENANT = "test-tenant";

    @Configuration
    @AutoConfigurationPackage
    @EnableJpaRepositories(basePackageClasses = NotificationPreferencesEntityRepository.class)
    @EntityScan(basePackageClasses = {NotificationPreferencesEntity.class, MuteRuleEntity.class, SnoozeEntity.class})
    static class TestConfig {
        @Bean
        SpringNotificationPreferenceStore prefStore(NotificationPreferencesEntityRepository repo) {
            return new SpringNotificationPreferenceStore(repo);
        }

        @Bean
        SpringSuppressionStore suppressionStore(MuteRuleEntityRepository muteRepo, SnoozeEntityRepository snoozeRepo) {
            return new SpringSuppressionStore(muteRepo, snoozeRepo);
        }
    }

    @Autowired SpringNotificationPreferenceStore prefStore;
    @Autowired SpringSuppressionStore suppressionStore;

    @Test
    void getReturnsEmptyWhenNotSet() {
        assertTrue(prefStore.get(USER, TENANT).isEmpty());
    }

    @Test
    void updateCreatesAndReturns() {
        var update = new NotificationPreferenceUpdate(
                Map.of("email", new ChannelPreference(true, NotificationSeverity.INFO, null, null)),
                null, false);
        NotificationPreferences prefs = prefStore.update(USER, TENANT, update);
        assertEquals(USER, prefs.userId());
        assertTrue(prefs.channelDefaults().containsKey("email"));
    }

    @Test
    void updateMergesExisting() {
        var first = new NotificationPreferenceUpdate(
                Map.of("email", new ChannelPreference(true, NotificationSeverity.INFO, null, null)),
                null, false);
        prefStore.update(USER, TENANT, first);

        var second = new NotificationPreferenceUpdate(
                Map.of("sms", new ChannelPreference(false, NotificationSeverity.INFO, null, null)),
                null, false);
        NotificationPreferences prefs = prefStore.update(USER, TENANT, second);
        assertTrue(prefs.channelDefaults().containsKey("sms"));
    }

    @Test
    void getReturnsAfterUpdate() {
        var update = new NotificationPreferenceUpdate(
                Map.of("email", new ChannelPreference(true, NotificationSeverity.INFO, null, null)),
                null, false);
        prefStore.update(USER, TENANT, update);

        Optional<NotificationPreferences> result = prefStore.get(USER, TENANT);
        assertTrue(result.isPresent());
        assertEquals(USER, result.get().userId());
    }

    @Test
    void addAndGetActiveMutes() {
        var input = new MuteRuleInput(USER, TENANT, MuteScope.CATEGORY, "alerts", null,
                Instant.now().plus(1, ChronoUnit.HOURS));
        MuteRule rule = suppressionStore.addMute(input);
        assertNotNull(rule.id());

        List<MuteRule> active = suppressionStore.activeMutes(USER, TENANT);
        assertEquals(1, active.size());
        assertEquals("alerts", active.getFirst().scopeId());
    }

    @Test
    void expiredMutesNotReturned() {
        var input = new MuteRuleInput(USER, TENANT, MuteScope.CATEGORY, "alerts", null,
                Instant.now().minus(1, ChronoUnit.HOURS));
        suppressionStore.addMute(input);

        List<MuteRule> active = suppressionStore.activeMutes(USER, TENANT);
        assertTrue(active.isEmpty());
    }

    @Test
    void removeMute() {
        var input = new MuteRuleInput(USER, TENANT, MuteScope.CATEGORY, "alerts", null, null);
        MuteRule rule = suppressionStore.addMute(input);

        assertTrue(suppressionStore.removeMute(rule.id(), USER, TENANT));
        assertTrue(suppressionStore.activeMutes(USER, TENANT).isEmpty());
    }

    @Test
    void activateAndGetSnooze() {
        var input = new SnoozeInput(USER, TENANT, Instant.now().plus(1, ChronoUnit.HOURS));
        Snooze snooze = suppressionStore.activateSnooze(input);
        assertEquals(USER, snooze.userId());

        Optional<Snooze> active = suppressionStore.activeSnooze(USER, TENANT);
        assertTrue(active.isPresent());
    }

    @Test
    void expiredSnoozeNotReturned() {
        var input = new SnoozeInput(USER, TENANT, Instant.now().minus(1, ChronoUnit.HOURS));
        suppressionStore.activateSnooze(input);

        assertTrue(suppressionStore.activeSnooze(USER, TENANT).isEmpty());
    }

    @Test
    void cancelSnooze() {
        var input = new SnoozeInput(USER, TENANT, Instant.now().plus(1, ChronoUnit.HOURS));
        suppressionStore.activateSnooze(input);

        assertTrue(suppressionStore.cancelSnooze(USER, TENANT));
        assertTrue(suppressionStore.activeSnooze(USER, TENANT).isEmpty());
    }
}
