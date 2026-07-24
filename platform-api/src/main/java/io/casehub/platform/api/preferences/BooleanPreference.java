package io.casehub.platform.api.preferences;

import java.util.Locale;
import java.util.Objects;

public record BooleanPreference(boolean value) implements SingleValuePreference {
    public static BooleanPreference of(boolean value) {
        return new BooleanPreference(value);
    }

    public static BooleanPreference parse(String raw) {
        Objects.requireNonNull(raw, "raw must not be null");
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "true" -> new BooleanPreference(true);
            case "false" -> new BooleanPreference(false);
            default -> throw new IllegalArgumentException(
                    "Invalid boolean value: '" + raw + "' — expected 'true' or 'false'");
        };
    }

    @Override
    public String toSerializedValue() {
        return String.valueOf(value);
    }
}
