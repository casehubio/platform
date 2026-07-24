package io.casehub.platform.api.preferences;

import java.util.Objects;

public record IntPreference(int value) implements SingleValuePreference {
    public static IntPreference of(int value) {
        return new IntPreference(value);
    }

    public static IntPreference parse(String raw) {
        Objects.requireNonNull(raw, "raw must not be null");
        return new IntPreference(Integer.parseInt(raw));
    }

    @Override
    public String toSerializedValue() {
        return String.valueOf(value);
    }

}
