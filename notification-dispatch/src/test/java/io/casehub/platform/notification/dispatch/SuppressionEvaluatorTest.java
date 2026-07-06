package io.casehub.platform.notification.dispatch;

import io.casehub.platform.api.notification.settings.MuteRule;
import io.casehub.platform.api.notification.settings.MuteScope;
import io.casehub.platform.api.notification.settings.QuietHours;
import io.casehub.platform.api.notification.settings.Snooze;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SuppressionEvaluatorTest {

    private final SuppressionEvaluator evaluator = new SuppressionEvaluator();

    private static final String USER = "user-1";
    private static final String TENANT = "tenant-1";
    private static final Instant NOW = Instant.now();

    @Test
    void evaluate_noMutesNoSnooze_returnsAllFalse() {
        var result = evaluator.evaluate(
                List.of(), Optional.empty(), null,
                "work-item", "wi-123", "comment");

        assertThat(result.isMuted()).isFalse();
        assertThat(result.isSnoozed()).isFalse();
        assertThat(result.quietHoursActive()).isFalse();
    }

    @Test
    void evaluate_entityMuteMatches_isMutedTrue() {
        var mute = new MuteRule("m-1", USER, TENANT, MuteScope.ENTITY,
                "wi-123", "work-item", NOW, null);

        var result = evaluator.evaluate(
                List.of(mute), Optional.empty(), null,
                "work-item", "wi-123", "comment");

        assertThat(result.isMuted()).isTrue();
    }

    @Test
    void evaluate_entityMute_differentEntityId_notMuted() {
        var mute = new MuteRule("m-1", USER, TENANT, MuteScope.ENTITY,
                "wi-999", "work-item", NOW, null);

        var result = evaluator.evaluate(
                List.of(mute), Optional.empty(), null,
                "work-item", "wi-123", "comment");

        assertThat(result.isMuted()).isFalse();
    }

    @Test
    void evaluate_entityMute_differentEntityType_notMuted() {
        var mute = new MuteRule("m-1", USER, TENANT, MuteScope.ENTITY,
                "wi-123", "case", NOW, null);

        var result = evaluator.evaluate(
                List.of(mute), Optional.empty(), null,
                "work-item", "wi-123", "comment");

        assertThat(result.isMuted()).isFalse();
    }

    @Test
    void evaluate_categoryMuteMatches_isMutedTrue() {
        var mute = new MuteRule("m-1", USER, TENANT, MuteScope.CATEGORY,
                "comment", null, NOW, null);

        var result = evaluator.evaluate(
                List.of(mute), Optional.empty(), null,
                "work-item", "wi-123", "comment");

        assertThat(result.isMuted()).isTrue();
    }

    @Test
    void evaluate_categoryMuteWithEntityType_matchesBoth() {
        var mute = new MuteRule("m-1", USER, TENANT, MuteScope.CATEGORY,
                "comment", "work-item", NOW, null);

        var result = evaluator.evaluate(
                List.of(mute), Optional.empty(), null,
                "work-item", "wi-123", "comment");

        assertThat(result.isMuted()).isTrue();
    }

    @Test
    void evaluate_categoryMuteWithEntityType_noMatchOnDifferentEntity() {
        var mute = new MuteRule("m-1", USER, TENANT, MuteScope.CATEGORY,
                "comment", "case", NOW, null);

        var result = evaluator.evaluate(
                List.of(mute), Optional.empty(), null,
                "work-item", "wi-123", "comment");

        assertThat(result.isMuted()).isFalse();
    }

    @Test
    void evaluate_expiredMute_ignored() {
        var mute = new MuteRule("m-1", USER, TENANT, MuteScope.ENTITY,
                "wi-123", "work-item", NOW.minus(2, ChronoUnit.HOURS),
                NOW.minus(1, ChronoUnit.HOURS));

        var result = evaluator.evaluate(
                List.of(mute), Optional.empty(), null,
                "work-item", "wi-123", "comment");

        assertThat(result.isMuted()).isFalse();
    }

    @Test
    void evaluate_activeSnooze_isSnoozedTrue() {
        var snooze = new Snooze(USER, TENANT,
                NOW.plus(1, ChronoUnit.HOURS), NOW);

        var result = evaluator.evaluate(
                List.of(), Optional.of(snooze), null,
                "work-item", "wi-123", "comment");

        assertThat(result.isSnoozed()).isTrue();
    }

    @Test
    void evaluate_expiredSnooze_isSnoozedFalse() {
        var snooze = new Snooze(USER, TENANT,
                NOW.minus(1, ChronoUnit.HOURS), NOW.minus(2, ChronoUnit.HOURS));

        var result = evaluator.evaluate(
                List.of(), Optional.of(snooze), null,
                "work-item", "wi-123", "comment");

        assertThat(result.isSnoozed()).isFalse();
    }

    @Test
    void evaluate_quietHoursActive_sameDayWindow() {
        // Create quiet hours that span the current time
        var zone = ZoneId.systemDefault();
        var nowLocal = LocalTime.now(zone);
        var start = nowLocal.minusHours(1);
        var end = nowLocal.plusHours(1);
        var quietHours = new QuietHours(start, end, zone);

        var result = evaluator.evaluate(
                List.of(), Optional.empty(), quietHours,
                "work-item", "wi-123", "comment");

        assertThat(result.quietHoursActive()).isTrue();
    }

    @Test
    void evaluate_quietHoursActive_crossMidnight() {
        // Cross-midnight: 22:00 to 07:00 — test at 23:00
        var zone = ZoneId.of("UTC");
        var quietHours = new QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0), zone);

        // The evaluator checks against Instant.now(), so we test the logic
        // by creating hours that definitely contain now or definitely don't
        var nowLocal = LocalTime.now(zone);
        boolean shouldBeActive = nowLocal.isAfter(LocalTime.of(22, 0))
                || nowLocal.equals(LocalTime.of(22, 0))
                || nowLocal.isBefore(LocalTime.of(7, 0));

        var result = evaluator.evaluate(
                List.of(), Optional.empty(), quietHours,
                "work-item", "wi-123", "comment");

        assertThat(result.quietHoursActive()).isEqualTo(shouldBeActive);
    }

    @Test
    void evaluate_quietHoursInactive_outsideWindow() {
        // Window that is definitely NOT now: 3am-4am if current time is mid-day
        // Use a timezone trick: pick a zone where it's guaranteed to be outside
        var zone = ZoneId.systemDefault();
        var nowLocal = LocalTime.now(zone);
        // Create a 1-minute window starting 2 hours from now
        var start = nowLocal.plusHours(2);
        var end = start.plusMinutes(1);
        var quietHours = new QuietHours(start, end, zone);

        var result = evaluator.evaluate(
                List.of(), Optional.empty(), quietHours,
                "work-item", "wi-123", "comment");

        assertThat(result.quietHoursActive()).isFalse();
    }

    @Test
    void evaluate_noQuietHours_quietHoursActiveFalse() {
        var result = evaluator.evaluate(
                List.of(), Optional.empty(), null,
                "work-item", "wi-123", "comment");

        assertThat(result.quietHoursActive()).isFalse();
    }

    @Test
    void evaluate_multipleMutes_firstMatchWins() {
        var mute1 = new MuteRule("m-1", USER, TENANT, MuteScope.ENTITY,
                "wi-999", "work-item", NOW, null); // no match
        var mute2 = new MuteRule("m-2", USER, TENANT, MuteScope.CATEGORY,
                "comment", null, NOW, null); // match

        var result = evaluator.evaluate(
                List.of(mute1, mute2), Optional.empty(), null,
                "work-item", "wi-123", "comment");

        assertThat(result.isMuted()).isTrue();
    }

    @Test
    void evaluate_allThreeActive() {
        var mute = new MuteRule("m-1", USER, TENANT, MuteScope.ENTITY,
                "wi-123", "work-item", NOW, null);
        var snooze = new Snooze(USER, TENANT,
                NOW.plus(1, ChronoUnit.HOURS), NOW);
        var zone = ZoneId.systemDefault();
        var nowLocal = LocalTime.now(zone);
        var quietHours = new QuietHours(
                nowLocal.minusHours(1), nowLocal.plusHours(1), zone);

        var result = evaluator.evaluate(
                List.of(mute), Optional.of(snooze), quietHours,
                "work-item", "wi-123", "comment");

        assertThat(result.isMuted()).isTrue();
        assertThat(result.isSnoozed()).isTrue();
        assertThat(result.quietHoursActive()).isTrue();
    }
}
