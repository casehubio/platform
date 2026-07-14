package io.casehub.platform.api.preferences;

import java.util.Objects;

public record DoublePreference(double value) implements SingleValuePreference {
    public static DoublePreference of(double value) {
        return new DoublePreference(value);
    }

    public static DoublePreference parse(String raw) {
        Objects.requireNonNull(raw, "raw must not be null");
        return new DoublePreference(Double.parseDouble(raw));
    }
}
