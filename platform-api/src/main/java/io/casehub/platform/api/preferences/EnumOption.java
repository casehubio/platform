package io.casehub.platform.api.preferences;

import java.util.Objects;

public record EnumOption(String value, String label) {
    public EnumOption {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(label, "label");
    }
}
