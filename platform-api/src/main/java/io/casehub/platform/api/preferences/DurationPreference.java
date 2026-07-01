package io.casehub.platform.api.preferences;

import java.time.Duration;
import java.util.Objects;

public record DurationPreference(Duration duration) implements MultiValuePreference {
    public DurationPreference {
        Objects.requireNonNull(duration, "duration must not be null");
    }
}
