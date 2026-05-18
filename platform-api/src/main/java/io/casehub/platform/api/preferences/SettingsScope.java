package io.casehub.platform.api.preferences;

import io.casehub.platform.api.path.Path;
import java.time.Instant;
import java.util.Objects;

public record SettingsScope(Path scope, Instant effectiveAt) {

    public SettingsScope {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(effectiveAt, "effectiveAt must not be null");
    }

    public static SettingsScope of(Path scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        return new SettingsScope(scope, Instant.now());
    }

    public static SettingsScope of(String... segments) {
        return new SettingsScope(Path.of(segments), Instant.now());
    }
}
