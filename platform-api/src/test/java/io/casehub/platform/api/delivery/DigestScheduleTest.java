package io.casehub.platform.api.delivery;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DigestScheduleTest {

    @Test
    void interval_rejectsNull() {
        assertThatThrownBy(() -> new DigestSchedule.Interval(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void interval_rejectsZero() {
        assertThatThrownBy(() -> new DigestSchedule.Interval(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("period must be >=");
    }

    @Test
    void interval_rejectsBelowMinimum() {
        assertThatThrownBy(() -> new DigestSchedule.Interval(Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void interval_acceptsOneMinute() {
        var interval = new DigestSchedule.Interval(Duration.ofMinutes(1));
        assertThat(interval.period()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void interval_isFlushDue_trueWhenPeriodElapsed() {
        var interval = new DigestSchedule.Interval(Duration.ofHours(4));
        Instant oldest = Instant.parse("2026-07-06T08:00:00Z");
        Instant lastFlush = Instant.parse("2026-07-06T07:00:00Z");
        Instant now = Instant.parse("2026-07-06T12:00:01Z");
        assertThat(interval.isFlushDue(oldest, lastFlush, now)).isTrue();
    }

    @Test
    void interval_isFlushDue_falseWhenPeriodNotElapsed() {
        var interval = new DigestSchedule.Interval(Duration.ofHours(4));
        Instant oldest = Instant.parse("2026-07-06T08:00:00Z");
        Instant lastFlush = Instant.parse("2026-07-06T07:00:00Z");
        Instant now = Instant.parse("2026-07-06T11:59:59Z");
        assertThat(interval.isFlushDue(oldest, lastFlush, now)).isFalse();
    }

    @Test
    void dailyAt_rejectsNullTime() {
        assertThatThrownBy(() -> new DigestSchedule.DailyAt(null, ZoneId.of("UTC")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void dailyAt_rejectsNullTimezone() {
        assertThatThrownBy(() -> new DigestSchedule.DailyAt(LocalTime.of(9, 0), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void dailyAt_isFlushDue_trueWhenTargetTimePassedAndNotFlushedToday() {
        var daily = new DigestSchedule.DailyAt(LocalTime.of(9, 0), ZoneId.of("UTC"));
        Instant oldest = Instant.parse("2026-07-06T06:00:00Z");
        Instant lastFlush = Instant.parse("2026-07-05T09:00:00Z");
        Instant now = Instant.parse("2026-07-06T09:00:01Z");
        assertThat(daily.isFlushDue(oldest, lastFlush, now)).isTrue();
    }

    @Test
    void dailyAt_isFlushDue_falseWhenAlreadyFlushedToday() {
        var daily = new DigestSchedule.DailyAt(LocalTime.of(9, 0), ZoneId.of("UTC"));
        Instant oldest = Instant.parse("2026-07-06T09:30:00Z");
        Instant lastFlush = Instant.parse("2026-07-06T09:00:00Z");
        Instant now = Instant.parse("2026-07-06T10:00:00Z");
        assertThat(daily.isFlushDue(oldest, lastFlush, now)).isFalse();
    }

    @Test
    void dailyAt_isFlushDue_falseBeforeTargetTime() {
        var daily = new DigestSchedule.DailyAt(LocalTime.of(9, 0), ZoneId.of("UTC"));
        Instant oldest = Instant.parse("2026-07-06T06:00:00Z");
        Instant lastFlush = Instant.parse("2026-07-05T09:00:00Z");
        Instant now = Instant.parse("2026-07-06T08:59:59Z");
        assertThat(daily.isFlushDue(oldest, lastFlush, now)).isFalse();
    }
}
