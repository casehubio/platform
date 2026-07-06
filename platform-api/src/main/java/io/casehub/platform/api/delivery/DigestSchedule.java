package io.casehub.platform.api.delivery;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DigestSchedule.Interval.class, name = "interval"),
        @JsonSubTypes.Type(value = DigestSchedule.DailyAt.class, name = "daily_at")
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
}
