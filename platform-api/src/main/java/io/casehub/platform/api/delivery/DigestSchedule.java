package io.casehub.platform.api.delivery;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DigestSchedule.Interval.class, name = "interval"),
        @JsonSubTypes.Type(value = DigestSchedule.DailyAt.class, name = "daily_at"),
        @JsonSubTypes.Type(value = DigestSchedule.WeeklyAt.class, name = "weekly_at")
})
public sealed interface DigestSchedule {

    boolean isFlushDue(Instant oldestPending, Instant lastFlush, Instant now);

    record Interval(Duration period) implements DigestSchedule {
        private static final Duration MIN_PERIOD = Duration.ofMinutes(1);

        public Interval {
            Objects.requireNonNull(period, "period");
            if (period.compareTo(MIN_PERIOD) < 0)
                throw new IllegalArgumentException("period must be >= " + MIN_PERIOD);
        }

        @Override
        public boolean isFlushDue(Instant oldestPending, Instant lastFlush, Instant now) {
            return !oldestPending.plus(period).isAfter(now);
        }
    }

    record DailyAt(LocalTime time, ZoneId timezone) implements DigestSchedule {
        public DailyAt {
            Objects.requireNonNull(time, "time");
            Objects.requireNonNull(timezone, "timezone");
        }

        @Override
        public boolean isFlushDue(Instant oldestPending, Instant lastFlush, Instant now) {
            Instant todayTarget = now.atZone(timezone).with(time).toInstant();
            return !now.isBefore(todayTarget) && lastFlush.isBefore(todayTarget);
        }
    }

    record WeeklyAt(DayOfWeek day, LocalTime time, ZoneId timezone) implements DigestSchedule {
        public WeeklyAt {
            Objects.requireNonNull(day, "day");
            Objects.requireNonNull(time, "time");
            Objects.requireNonNull(timezone, "timezone");
        }

        @Override
        public boolean isFlushDue(Instant oldestPending, Instant lastFlush, Instant now) {
            ZonedDateTime zoned = now.atZone(timezone);
            ZonedDateTime thisWeekTarget = zoned.with(TemporalAdjusters.previousOrSame(day)).with(time);
            if (thisWeekTarget.isAfter(zoned)) {
                thisWeekTarget = thisWeekTarget.minusWeeks(1);
            }
            Instant target = thisWeekTarget.toInstant();
            return !now.isBefore(target) && lastFlush.isBefore(target);
        }
    }
}
